package com.dvid.dcam.platform.device.capability.probe.egl;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.view.Surface;
import androidx.annotation.RequiresApi;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.platform.camera.shared.CameraPipelineIds;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public final class EglFanOutFastProbe {
    public static final VerificationPipelineId PIPELINE_ID =
            CameraPipelineIds.EGL_FAN_OUT;
    private static final long OPEN_TIMEOUT_MILLIS = 5_000;
    private static final long SESSION_TIMEOUT_MILLIS = 3_000;
    private static final long RELEASE_TIMEOUT_MILLIS = 2_000;
    private static final long EGL_TIMEOUT_MILLIS = 5_000;
    private static final String CAMERA_SOURCES = "SurfaceTexture|ImageReader";
    private static final String DOWNSTREAM_SOURCES = "SurfaceTexture+MediaCodec";
    private static final String DETAIL = "detail";
    private static final String OPEN_THREAD = "open_thread";
    private static final String GL_EXTERNAL_TEXTURE = "gl_external_texture";
    private static final String EGL_CLEANUP_PREFIX = "egl_cleanup:";
    private final Context context;
    private final Logger logger;

    public EglFanOutFastProbe(Context context, Logger logger) {
        Context app=Objects.requireNonNull(context,"context").getApplicationContext();
        this.context=app==null?context:app;
        this.logger=Objects.requireNonNull(logger,"logger");
    }

    public Result probe(CameraId cameraId, Collection<VideoMode> videos,
            Collection<ImageMode> images) {
        Objects.requireNonNull(cameraId,"cameraId");
        long imageProfileCount=Objects.requireNonNull(images,"images").stream().distinct().count();
        long started=System.nanoTime();
        ResultContext resultContext = new ResultContext(cameraId, started);
        EglWorker egl;
        try { egl=EglWorker.open(cameraId); }
        catch (EglUnavailable unavailable) {
            Result result=result(resultContext,PipelineAvailability.UNAVAILABLE,
                    Completion.PIPELINE_UNAVAILABLE,List.of(),List.of(),true,
                    unavailable.getMessage());
            logComplete(result,imageProfileCount); return result;
        } catch (ProbeFailure failure) {
            Result result=incomplete(resultContext,List.of(),List.of(),false,failure);
            logFailure(cameraId,"egl_init",failure); logComplete(result,imageProfileCount); return result;
        }
        OpenedCamera camera = null;
        Result result = null;
        ProbeFailure cameraCleanup = null;
        ProbeFailure eglCleanup = null;
        try {
            logger.info(prefix(cameraId)+" stage=egl_init result=available"
                    +" cameraOutputCount=2 downstreamSurfaceCount=2"
                    +" cameraSurfaceClasses="+CAMERA_SOURCES
                    +" downstreamSurfaceClasses="+DOWNSTREAM_SOURCES);
            try { camera=OpenedCamera.open(context,cameraId); }
            catch (ProbeFailure failure) {
                result=incomplete(resultContext,List.of(),List.of(),false,failure);
                logFailure(cameraId,"open",failure);
            }
            if(result==null) {
                OpenedCamera openedCamera=camera;
                try {
                    result=runMatrix(cameraId,videos,images,
                            (tuple,attempt,confirmation)->query(openedCamera,egl,tuple), started);
                } catch(RuntimeException error) {
                    ProbeFailure failure=ProbeFailure.global(
                            "probe:"+error.getClass().getSimpleName(),error);
                    result=incomplete(resultContext,List.of(),List.of(),false,failure);
                    logFailure(cameraId,"probe",failure);
                }
            }
        } finally {
            if(camera!=null) {
                cameraCleanup=camera.release();
            }
            eglCleanup=egl.close();
            ProbeFailure cleanup=firstFailure(cameraCleanup,eglCleanup);
            if(result!=null) {
                if(cleanup!=null) {
                    result=incomplete(resultContext,result.evidence().rawFastCandidates(),
                            result.attempts(),false,cleanup);
                } else {
                    result=new Result(result.evidence(),result.completion(),result.attempts(),
                            true,result.elapsedMillis(),result.detail());
                }
            }
        }
        if(cameraCleanup!=null) {
            logFailure(cameraId,"camera_cleanup",cameraCleanup);
        }
        if(eglCleanup!=null) {
            logFailure(cameraId,"egl_cleanup",eglCleanup);
        }
        logComplete(result,imageProfileCount);
        return result;
    }

    static Result runMatrix(CameraId cameraId, Collection<VideoMode> videoModes,
            Collection<ImageMode> imageModes, TupleQuery query, long started) {
        MatrixContext context = new MatrixContext(cameraId, query, started);
        TreeSet<VideoMode> videos=sorted(videoModes,"videoModes");
        TreeSet<ImageMode> images=sorted(imageModes,"imageModes");
        List<CaptureModeTuple> universe=universe(videos, images);
        Result stop=runInitialAttempts(context, universe);
        if(stop!=null) {
            return stop;
        }
        stop=confirmFpsRows(context, videos, universe);
        if(stop!=null) {
            return stop;
        }
        stop=confirmVideoModes(context, videos, universe);
        if(stop!=null) {
            return stop;
        }
        return result(context.resultContext,PipelineAvailability.AVAILABLE,Completion.COMPLETE,
                supported(context.cameraId,context.states),context.attempts,false,"matrix_complete");
    }

    private static List<CaptureModeTuple> universe(
            Collection<VideoMode> videos, Collection<ImageMode> images) {
        List<CaptureModeTuple> universe=new ArrayList<>();
        for (VideoMode video : videos) {
            for (ImageMode image : images) {
                universe.add(new CaptureModeTuple(video, image));
            }
        }
        return universe;
    }

    private static Result runInitialAttempts(MatrixContext context,
            List<CaptureModeTuple> universe) {
        for(CaptureModeTuple tuple:universe) {
            Result stop=attempt(context,tuple,1,"initial");
            if(stop!=null) {
                return stop;
            }
        }
        return null;
    }

    private static Result confirmFpsRows(MatrixContext context,
            Collection<VideoMode> videos, List<CaptureModeTuple> universe) {
        for(VideoMode video:videos) {
            Result stop=confirm(context,tuplesForVideo(universe, video),"fps_row");
            if(stop!=null) {
                return stop;
            }
        }
        return null;
    }

    private static Result confirmVideoModes(MatrixContext context,
            Collection<VideoMode> videos, List<CaptureModeTuple> universe) {
        TreeSet<StandardResolution> resolutions=new TreeSet<>();
        videos.forEach(v->resolutions.add(v.resolution()));
        for(StandardResolution resolution:resolutions) {
            Result stop=confirm(context,tuplesForResolution(universe, resolution),"video_mode");
            if(stop!=null) {
                return stop;
            }
        }
        return null;
    }

    private static List<CaptureModeTuple> tuplesForVideo(
            List<CaptureModeTuple> universe, VideoMode video) {
        return universe.stream().filter(t->t.videoMode().equals(video))
                .collect(java.util.stream.Collectors.toList());
    }

    private static List<CaptureModeTuple> tuplesForResolution(
            List<CaptureModeTuple> universe, StandardResolution resolution) {
        return universe.stream().filter(t->t.videoMode().resolution().equals(resolution))
                .collect(java.util.stream.Collectors.toList());
    }

    private static Result confirm(MatrixContext context,List<CaptureModeTuple> tuples,String reason) {
        if(tuples.isEmpty()||!tuples.stream().allMatch(t->context.states.get(t)==AttemptResult.REJECTED)) {
            return null;
        }
        for(CaptureModeTuple tuple:tuples) {
            if(context.states.get(tuple)==AttemptResult.REJECTED&&context.confirmed.add(tuple)) {
                Result stop=attempt(context,tuple,2,reason);
                if(stop!=null) {
                    return stop;
                }
            }
        }
        return null;
    }

    private static Result attempt(MatrixContext context,CaptureModeTuple tuple,int number,String reason) {
        long tick=System.nanoTime();
        Decision decision;
        try {
            decision=Objects.requireNonNull(context.query.query(tuple,number,reason),"decision");
        } catch(RuntimeException error) {
            decision=Decision.unbuilt(AttemptResult.INCOMPLETE_GLOBAL,
                    "query:"+error.getClass().getSimpleName());
        }
        Attempt attempt=new Attempt(tuple,decision.result(),number,reason,
                decision.cameraOutputCount(),decision.downstreamSurfaceCount(),
                decision.cameraSurfaceClasses(),decision.downstreamSurfaceClasses(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-tick),decision.detail());
        context.attempts.add(attempt);

        if(decision.result()==AttemptResult.SUPPORTED||decision.result()==AttemptResult.REJECTED) {
            context.states.put(tuple,decision.result());
            return null;
        }
        if(decision.result()==AttemptResult.PIPELINE_UNAVAILABLE) {
            return result(context.resultContext,PipelineAvailability.UNAVAILABLE,
                    Completion.PIPELINE_UNAVAILABLE,List.of(),context.attempts,false,decision.detail());
        }
        ProbeFailure failure=new ProbeFailure(completion(decision.result()),decision.detail(),null);
        return incomplete(context.resultContext,supported(context.cameraId,context.states),
                context.attempts,false,failure);
    }
    private Decision query(OpenedCamera camera,EglWorker egl,CaptureModeTuple tuple) {
        TupleResources resources;
        try { camera.ensureAvailable(); resources=egl.create(tuple); }
        catch (TupleRejected rejected) { return Decision.unbuilt(AttemptResult.REJECTED,rejected.getMessage()); }
        catch (EglUnavailable unavailable) { return Decision.unbuilt(AttemptResult.PIPELINE_UNAVAILABLE,unavailable.getMessage()); }
        catch (ProbeFailure failure) { return Decision.from(failure,false); }
        catch (RuntimeException error) { return Decision.unbuilt(AttemptResult.INCOMPLETE_GLOBAL,
                "create:"+error.getClass().getSimpleName()); }
        catch (OutOfMemoryError error) { return Decision.unbuilt(AttemptResult.INCOMPLETE_TRANSIENT,
                "create:out_of_memory"); }
        boolean supported;
        Decision decision;
        try {
            supported=Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q
                    ?camera.supports(resources.outputs()):camera.configures(resources.outputs());
            camera.ensureAvailable();
            decision=supported?Decision.supported("session_supported")
                    :Decision.rejected("session_rejected");
        } catch(CameraAccessException error) { decision=Decision.from(cameraFailure("query",error),true); }
        catch(ProbeFailure failure) { decision=Decision.from(failure,true); }
        catch(SecurityException error) { decision=Decision.topology(AttemptResult.BLOCKED_EXTERNAL,"query:permission"); }
        catch(RuntimeException error) { decision=Decision.topology(AttemptResult.INCOMPLETE_GLOBAL,
                "query:"+error.getClass().getSimpleName()); }
        ProbeFailure release=egl.release(resources);
        return release==null?decision:Decision.from(release,true);
    }

    private static List<CandidateKey> supported(CameraId cameraId,
            Map<CaptureModeTuple,AttemptResult> states) {
        List<CandidateKey> result=new ArrayList<>();
        states.forEach((tuple, state) -> {
            if (state == AttemptResult.SUPPORTED) {
                result.add(CandidateKey.forTuple(cameraId, VideoCodec.H264, PIPELINE_ID, tuple));
            }
        });
        return List.copyOf(result);
    }

    private static <T extends Comparable<? super T>> TreeSet<T> sorted(Collection<T> values,String name) {
        TreeSet<T> result=new TreeSet<>();
        for (T value : Objects.requireNonNull(values, name)) {
            result.add(Objects.requireNonNull(value, name));
        }
        return result;
    }

    private static Result incomplete(ResultContext context,Collection<CandidateKey> candidates,
            Collection<Attempt> attempts,boolean cleanup,ProbeFailure failure) {
        return result(context,PipelineAvailability.UNKNOWN,failure.completion(),candidates,
                attempts,cleanup,failure.detail());
    }

    private static Result result(ResultContext context,PipelineAvailability availability,
            Completion completion,Collection<CandidateKey> candidates,Collection<Attempt> attempts,
            boolean cleanup,String detail) {
        PipelineEvidence evidence=new PipelineEvidence(context.cameraId,VideoCodec.H264,PIPELINE_ID,
                availability,candidates,List.of());
        return new Result(evidence,completion,List.copyOf(attempts),cleanup,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-context.started),required(detail, DETAIL));
    }

    private void logComplete(Result result,long imageProfileCount) {
        logger.info(completeLog(result,imageProfileCount));
    }
    static String completeLog(Result result,long imageProfileCount) {
        PipelineEvidence evidence=result.evidence();
        return prefix(evidence.cameraId())+" stage=complete result="+id(result.completion())
                +" vfProfileCount="+vfProfileCount(evidence)
                +" imageProfileCount="+fastBuildImageProfileCount(evidence,imageProfileCount)
                +" tupleProfileCount="+tupleProfileCount(evidence)
                +" attemptCount="+result.attempts().size()+" cleanupComplete="+result.cleanupComplete()
                +" elapsedMs="+result.elapsedMillis()+" detail="+result.detail();
    }
    private static long vfProfileCount(PipelineEvidence evidence) {
        return evidence.rawFastCandidates().stream()
                .filter(candidate->candidate.videoMode().isPresent())
                .map(candidate->candidate.videoMode().orElseThrow()).distinct().count();
    }
    private static long fastBuildImageProfileCount(PipelineEvidence evidence,long imageProfileCount) {
        return evidence.availability()==PipelineAvailability.UNAVAILABLE?0:imageProfileCount;
    }
    private static long tupleProfileCount(PipelineEvidence evidence) {
        return evidence.rawFastCandidates().stream()
                .filter(candidate->candidate.kind()==CandidateKey.Kind.TUPLE).count();
    }
    private void logFailure(CameraId cameraId,String stage,ProbeFailure failure) {
        logger.warn(prefix(cameraId)+" stage="+stage+" result="+id(failure.completion())
                +" detail="+failure.detail(),failure);
    }
    private static String prefix(CameraId cameraId) { return "camera_fast_probe cameraId="+cameraId
            +" pipeline="+PIPELINE_ID+" codec=h264"; }
    private static String id(Enum<?> value) { return value.name().toLowerCase(java.util.Locale.ROOT); }
    private static Completion completion(AttemptResult result) { return switch(result) {
        case INCOMPLETE_TRANSIENT->Completion.INCOMPLETE_TRANSIENT;
        case INCOMPLETE_GLOBAL->Completion.INCOMPLETE_GLOBAL;
        case BLOCKED_EXTERNAL->Completion.BLOCKED_EXTERNAL;
        case PIPELINE_UNAVAILABLE->Completion.PIPELINE_UNAVAILABLE;
        case SUPPORTED,REJECTED->throw new IllegalArgumentException("not incomplete");
    };}

    private static ProbeFailure cameraFailure(String stage,CameraAccessException error) {
        return switch(error.getReason()) {
            case CameraAccessException.CAMERA_DISABLED->ProbeFailure.blocked(stage+":disabled",error);
            case CameraAccessException.CAMERA_IN_USE,CameraAccessException.MAX_CAMERAS_IN_USE,
                    CameraAccessException.CAMERA_DISCONNECTED->ProbeFailure.transientFailure(
                    stage+":camera_access_"+error.getReason(),error);
            default->ProbeFailure.global(stage+":camera_access_"+error.getReason(),error);
        };
    }
    static boolean isTemporaryAllocationFailure(int error) {
        return error==EGL14.EGL_BAD_ALLOC||error==GLES20.GL_OUT_OF_MEMORY;
    }
    private static ProbeFailure firstFailure(ProbeFailure first,ProbeFailure second) {
        return first==null?second:first;
    }

    public enum Completion { COMPLETE,PIPELINE_UNAVAILABLE,INCOMPLETE_TRANSIENT,
        INCOMPLETE_GLOBAL,BLOCKED_EXTERNAL }
    public enum AttemptResult { SUPPORTED,REJECTED,PIPELINE_UNAVAILABLE,
        INCOMPLETE_TRANSIENT,INCOMPLETE_GLOBAL,BLOCKED_EXTERNAL }
    public record Attempt(CaptureModeTuple tuple,AttemptResult result,int attempt,String confirmation,
            int cameraOutputCount,int downstreamSurfaceCount,String cameraSurfaceClasses,
            String downstreamSurfaceClasses,long elapsedMillis,String detail) {
        public Attempt { Objects.requireNonNull(tuple);Objects.requireNonNull(result);
            if(attempt<=0||cameraOutputCount<0||downstreamSurfaceCount<0||elapsedMillis<0)
                throw new IllegalArgumentException("invalid attempt");
            required(confirmation,"confirmation");
            required(cameraSurfaceClasses,"cameraSurfaceClasses");
            required(downstreamSurfaceClasses,"downstreamSurfaceClasses");
            required(detail, DETAIL); }
    }
    public record Result(PipelineEvidence evidence,Completion completion,List<Attempt> attempts,
            boolean cleanupComplete,long elapsedMillis,String detail) {
        public Result { Objects.requireNonNull(evidence);Objects.requireNonNull(completion);
            attempts=List.copyOf(attempts);
            if(elapsedMillis<0) {
                throw new IllegalArgumentException();
            }
            required(detail, DETAIL); }
        public boolean complete(){return completion==Completion.COMPLETE;}
    }
    @FunctionalInterface interface TupleQuery { Decision query(CaptureModeTuple tuple,int attempt,String confirmation); }

    private record ResultContext(CameraId cameraId,long started) { }

    private static final class MatrixContext {
        private final ResultContext resultContext;
        private final CameraId cameraId;
        private final TupleQuery query;
        private final Map<CaptureModeTuple,AttemptResult> states=new TreeMap<>();
        private final List<Attempt> attempts=new ArrayList<>();
        private final Set<CaptureModeTuple> confirmed=new HashSet<>();

        private MatrixContext(CameraId cameraId,TupleQuery query,long started) {
            this.resultContext=new ResultContext(cameraId,started);
            this.cameraId=cameraId;
            this.query=query;
        }
    }

    record Decision(AttemptResult result,int cameraOutputCount,int downstreamSurfaceCount,
            String cameraSurfaceClasses,String downstreamSurfaceClasses,String detail) {
        Decision { Objects.requireNonNull(result);required(detail, DETAIL);
            required(cameraSurfaceClasses,"cameraSurfaceClasses");
            required(downstreamSurfaceClasses,"downstreamSurfaceClasses"); }
        static Decision supported(String d){return topology(AttemptResult.SUPPORTED,d);}
        static Decision rejected(String d){return topology(AttemptResult.REJECTED,d);}
        static Decision topology(AttemptResult r,String d){return new Decision(r,2,2,CAMERA_SOURCES,DOWNSTREAM_SOURCES,d);}
        static Decision unbuilt(AttemptResult r,String d){return new Decision(r,0,0,"unbuilt","unbuilt",d);}
        static Decision from(ProbeFailure f,boolean built){AttemptResult r=switch(f.completion()){
            case PIPELINE_UNAVAILABLE->AttemptResult.PIPELINE_UNAVAILABLE;
            case INCOMPLETE_TRANSIENT->AttemptResult.INCOMPLETE_TRANSIENT;
            case INCOMPLETE_GLOBAL->AttemptResult.INCOMPLETE_GLOBAL;
            case BLOCKED_EXTERNAL->AttemptResult.BLOCKED_EXTERNAL;
            case COMPLETE->throw new IllegalArgumentException();};return built?topology(r,f.detail()):unbuilt(r,f.detail());}
    }
    static final class OpenGuard<T> {
        private T value;
        private boolean abandoned;
        synchronized boolean accept(T candidate) {
            if(abandoned) {
                return false;
            }
            value=candidate;
            return true;
        }

        synchronized T abandon() {
            abandoned=true;
            T owned=value;
            value=null;
            return owned;
        }

        synchronized T value() {
            return value;
        }

        synchronized boolean abandoned() {
            return abandoned;
        }
    }

    private static final class OpenedCamera {
        private final CameraDevice camera;
        private final HandlerThread thread;
        private final Handler handler;
        private final AtomicReference<ProbeFailure> failure;
        private final CountDownLatch closed;

        private OpenedCamera(CameraDevice camera,HandlerThread thread,Handler handler,
                AtomicReference<ProbeFailure> failure,CountDownLatch closed){
            this.camera=camera;
            this.thread=thread;
            this.handler=handler;
            this.failure=failure;
            this.closed=closed;
        }

        private static OpenedCamera open(Context context,CameraId id)throws ProbeFailure{
            CameraManager manager=context.getSystemService(CameraManager.class);
            if(manager==null) {
                throw ProbeFailure.global("open:no_manager",null);
            }

            OpenRequest request=new OpenRequest(id);
            try{
                manager.openCamera(id.value(),request.callback(),request.handler);
                request.requestSubmitted=true;
                return request.awaitOpened();
            }catch(SecurityException error){
                return failOpen(request,ProbeFailure.blocked("open:permission",error));
            }catch(CameraAccessException error){
                return failOpen(request,cameraFailure("open",error));
            }catch(InterruptedException error){
                return failOpenInterrupted(request,error);
            }catch(RuntimeException error){
                return failOpen(request,ProbeFailure.global(
                        "open:"+error.getClass().getSimpleName(),error));
            }
        }

        private static OpenedCamera failOpen(OpenRequest request,ProbeFailure failure)
                throws ProbeFailure {
            ProbeFailure cleanup=request.cleanupAfterFailure();
            if(cleanup!=null) {
                throw cleanup;
            }
            throw failure;
        }

        private static OpenedCamera failOpenInterrupted(OpenRequest request,
                InterruptedException error)throws ProbeFailure{
            ProbeFailure cleanup=request.cleanupAfterFailure();
            Thread.currentThread().interrupt();
            if(cleanup!=null) {
                throw cleanup;
            }
            throw ProbeFailure.transientFailure("open:interrupted",error);
        }

        private static final class OpenRequest {
            private final HandlerThread thread;
            private final Handler handler;
            private final CountDownLatch opened=new CountDownLatch(1);
            private final CountDownLatch closed=new CountDownLatch(1);
            private final OpenGuard<CameraDevice> guard=new OpenGuard<>();
            private final AtomicReference<ProbeFailure> failure=new AtomicReference<>();
            private boolean requestSubmitted;

            private static ProbeFailure cameraStateFailure(int code) {
                return switch(code) {
                    case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED ->
                            ProbeFailure.blocked("camera_state:disabled",null);
                    case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE,
                            CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE ->
                            ProbeFailure.transientFailure("camera_state:error_"+code,null);
                    default -> ProbeFailure.global("camera_state:error_"+code,null);
                };
            }

            private OpenRequest(CameraId id) {
                thread=new HandlerThread("dcam-egl-fast-camera-"+id);
                thread.start();
                handler=new Handler(thread.getLooper());
            }

            private CameraDevice.StateCallback callback() {
                return new CameraDevice.StateCallback(){
                    @Override public void onOpened(CameraDevice camera){
                        if(!guard.accept(camera)) {
                            camera.close();
                        }
                        opened.countDown();
                    }

                    @Override public void onDisconnected(CameraDevice camera){
                        failure.compareAndSet(null,ProbeFailure.transientFailure(
                                "camera_state:disconnected",null));
                        camera.close();
                        opened.countDown();
                    }

                    @Override public void onError(CameraDevice camera,int code){
                        failure.compareAndSet(null,cameraStateFailure(code));
                        camera.close();
                        opened.countDown();
                    }

                    @Override public void onClosed(CameraDevice camera){
                        closed.countDown();
                        if(guard.abandoned()) {
                            thread.quitSafely();
                        }
                    }
                };
            }

            private OpenedCamera awaitOpened()throws ProbeFailure,InterruptedException{
                if(!opened.await(OPEN_TIMEOUT_MILLIS,TimeUnit.MILLISECONDS)){
                    ProbeFailure cleanup=abandonOpen();
                    if(cleanup!=null) {
                        throw cleanup;
                    }
                    throw ProbeFailure.transientFailure("open:timeout",null);
                }
                CameraDevice openedCamera=guard.value();
                ProbeFailure openFailure=failure.get();
                if(openedCamera==null||openFailure!=null){
                    ProbeFailure cleanup=abandonOpen();
                    if(cleanup!=null) {
                        throw cleanup;
                    }
                    throw openFailure==null?ProbeFailure.global("open:unknown",null):openFailure;
                }
                return new OpenedCamera(openedCamera,thread,handler,failure,closed);
            }

            private ProbeFailure cleanupAfterFailure() {
                return requestSubmitted?abandonOpen():stopThread(thread,OPEN_THREAD);
            }

            private ProbeFailure abandonOpen(){
                ProbeFailure cleanupFailure=null;
                CameraDevice owned=guard.abandon();
                if(owned!=null){
                    try{
                        owned.close();
                    }catch(RuntimeException error){
                        cleanupFailure=ProbeFailure.transientFailure("open_cleanup:"+
                                error.getClass().getSimpleName(),error);
                    }
                }
                if(closed.getCount()==0){
                    cleanupFailure=firstFailure(
                            cleanupFailure,stopThread(thread,OPEN_THREAD));
                }
                return cleanupFailure;
            }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private boolean supports(List<OutputConfiguration> outputs)
                throws CameraAccessException,ProbeFailure{
            ensureAvailable();
            SessionConfiguration config=new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,outputs,command->handler.post(command),
                    new CameraCaptureSession.StateCallback(){
                        @Override public void onConfigured(CameraCaptureSession session){
                            // Support checking never creates a live capture session.
                        }

                        @Override public void onConfigureFailed(CameraCaptureSession session){
                            // Support checking never creates a live capture session.
                        }
                    });
            return camera.isSessionConfigurationSupported(config);
        }

        private boolean configures(List<OutputConfiguration> outputs)throws ProbeFailure{
            CountDownLatch done=new CountDownLatch(1);
            CountDownLatch sessionClosed=new CountDownLatch(1);
            AtomicReference<Boolean> ok=new AtomicReference<>();
            AtomicReference<CameraCaptureSession> session=new AtomicReference<>();
            try{
                camera.createCaptureSessionByOutputConfigurations(outputs,
                        new CameraCaptureSession.StateCallback(){
                            @Override public void onConfigured(CameraCaptureSession value){
                                session.set(value);
                                ok.set(true);
                                done.countDown();
                            }

                            @Override public void onConfigureFailed(CameraCaptureSession value){
                                session.set(value);
                                ok.set(false);
                                value.close();
                                done.countDown();
                            }

                            @Override public void onClosed(CameraCaptureSession value){
                                sessionClosed.countDown();
                            }
                        },handler);
                if(!done.await(SESSION_TIMEOUT_MILLIS,TimeUnit.MILLISECONDS)){
                    CameraCaptureSession value=session.get();
                    if(value!=null) {
                        value.close();
                    }
                    throw ProbeFailure.transientFailure("session:timeout",null);
                }
                if(Boolean.TRUE.equals(ok.get())) {
                    session.get().close();
                }
                if(session.get()!=null&&
                        !sessionClosed.await(RELEASE_TIMEOUT_MILLIS,TimeUnit.MILLISECONDS)){
                    throw ProbeFailure.transientFailure("session:release_timeout",null);
                }
                ensureAvailable();
                return Boolean.TRUE.equals(ok.get());
            }catch(CameraAccessException error){
                throw cameraFailure("session",error);
            }catch(InterruptedException error){
                Thread.currentThread().interrupt();
                throw ProbeFailure.transientFailure("session:interrupted",error);
            }
        }

        private void ensureAvailable()throws ProbeFailure{
            if(failure.get()!=null) {
                throw failure.get();
            }
        }

        private ProbeFailure release(){
            ProbeFailure releaseFailure=null;
            try{
                camera.close();
                if(!closed.await(RELEASE_TIMEOUT_MILLIS,TimeUnit.MILLISECONDS)){
                    releaseFailure=ProbeFailure.transientFailure("camera_release:timeout",null);
                }
            }catch(InterruptedException error){
                Thread.currentThread().interrupt();
                releaseFailure=ProbeFailure.transientFailure("camera_release:interrupted",error);
            }catch(RuntimeException error){
                releaseFailure=ProbeFailure.transientFailure(
                        "camera_release:"+error.getClass().getSimpleName(),error);
            }
            return firstFailure(releaseFailure,stopThread(thread,"camera_thread"));
        }

        private static ProbeFailure stopThread(HandlerThread thread,String detail){
            thread.quitSafely();
            try{
                thread.join(RELEASE_TIMEOUT_MILLIS);
                if(thread.isAlive()){
                    thread.quit();
                    thread.join(RELEASE_TIMEOUT_MILLIS);
                }
            }catch(InterruptedException error){
                Thread.currentThread().interrupt();
                return ProbeFailure.transientFailure(detail+":interrupted",error);
            }
            return thread.isAlive()?ProbeFailure.transientFailure(detail+":timeout",null):null;
        }
    }
    private static final class EglWorker {
        private final HandlerThread thread;
        private final Handler handler;
        private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
        private EGLConfig config;
        private boolean initialized;
        private EGLContext context=EGL14.EGL_NO_CONTEXT;
        private EGLSurface pbuffer=EGL14.EGL_NO_SURFACE;

        private EglWorker(HandlerThread thread,Handler handler) {
            this.thread=thread;
            this.handler=handler;
        }

        private static EglWorker open(CameraId id)throws EglUnavailable,ProbeFailure {
            HandlerThread thread=new HandlerThread("dcam-egl-fast-"+id);
            thread.start();
            EglWorker worker=new EglWorker(thread,new Handler(thread.getLooper()));
            try {
                worker.call(()->{
                    worker.initialize();
                    return null;
                });
                return worker;
            } catch(EglUnavailable | ProbeFailure error) {
                ProbeFailure cleanup=worker.close();
                if(cleanup!=null) {
                    throw cleanup;
                }
                if(error instanceof EglUnavailable unavailable) {
                    throw unavailable;
                }
                throw (ProbeFailure) error;
            } catch(TupleRejected error) {
                ProbeFailure cleanup=worker.close();
                if(cleanup!=null) {
                    throw cleanup;
                }
                throw ProbeFailure.global("egl_init:unexpected_tuple",error);
            } catch(RuntimeException error) {
                ProbeFailure cleanup=worker.close();
                if(cleanup!=null) {
                    throw cleanup;
                }
                throw ProbeFailure.global("egl_init:"+error.getClass().getSimpleName(),error);
            } catch(OutOfMemoryError error) {
                ProbeFailure cleanup=worker.close();
                if(cleanup!=null) {
                    throw cleanup;
                }
                throw ProbeFailure.transientFailure("egl_init:out_of_memory",error);
            }
        }

        private void initialize()throws EglUnavailable,ProbeFailure {
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if(display==EGL14.EGL_NO_DISPLAY) {
                throwSetupFailure("egl_display",EGL14.eglGetError());
            }
            int[] version=new int[2];
            if(!EGL14.eglInitialize(display,version,0,version,1)) {
                throwSetupFailure("egl_initialize",EGL14.eglGetError());
            }
            initialized=true;
            int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
                    EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                    EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT|EGL14.EGL_PBUFFER_BIT,
                    EGLExt.EGL_RECORDABLE_ANDROID,1,EGL14.EGL_NONE};
            EGLConfig[] configs=new EGLConfig[1];
            int[] count=new int[1];
            if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)) {
                throwSetupFailure("egl_choose_config",EGL14.eglGetError());
            }
            if(count[0]==0) {
                throw new EglUnavailable("egl:no_recordable_config");
            }
            config=configs[0];
            context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            if(context==EGL14.EGL_NO_CONTEXT) {
                throwSetupFailure("egl_context",EGL14.eglGetError());
            }
            pbuffer=EGL14.eglCreatePbufferSurface(display,config,
                    new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
            if(pbuffer==EGL14.EGL_NO_SURFACE) {
                throwSetupFailure("egl_pbuffer",EGL14.eglGetError());
            }
            if(!EGL14.eglMakeCurrent(display,pbuffer,pbuffer,context)) {
                throwSetupFailure("egl_make_current",EGL14.eglGetError());
            }
        }

        private static void throwSetupFailure(String stage,int error)
                throws EglUnavailable,ProbeFailure {
            if(isTemporaryAllocationFailure(error)) {
                throw ProbeFailure.transientFailure(stage+":allocation",null);
            }
            throw new EglUnavailable(stage+":"+error);
        }
        private TupleResources create(CaptureModeTuple tuple)
                throws TupleRejected,EglUnavailable,ProbeFailure {
            return call(()->createOnThread(tuple));
        }

        private TupleResources createOnThread(CaptureModeTuple tuple)
                throws TupleRejected,EglUnavailable,ProbeFailure {
            int videoWidth=tuple.videoMode().resolution().actual().width();
            int videoHeight=tuple.videoMode().resolution().actual().height();
            int imageWidth=tuple.imageMode().resolution().actual().width();
            int imageHeight=tuple.imageMode().resolution().actual().height();
            TupleResources resources=new TupleResources();
            try {
                clearGlErrors();
                int[] texture=new int[1];
                GLES20.glGenTextures(1,texture,0);
                int glError=GLES20.glGetError();
                if(texture[0]==0) {
                    if(glError==GLES20.GL_NO_ERROR) {
                        throw new EglUnavailable("egl:no_external_texture");
                    }
                    throwSetupFailure(GL_EXTERNAL_TEXTURE,glError);
                }
                resources.cameraTextureId=texture[0];
                if(glError!=GLES20.GL_NO_ERROR) {
                    throwSetupFailure(GL_EXTERNAL_TEXTURE,glError);
                }
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture[0]);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
                glError=GLES20.glGetError();
                if(glError!=GLES20.GL_NO_ERROR) {
                    throwSetupFailure(GL_EXTERNAL_TEXTURE,glError);
                }
                resources.cameraTexture=new SurfaceTexture(texture[0]);
                resources.cameraTexture.setDefaultBufferSize(videoWidth,videoHeight);
                resources.cameraSurface=new Surface(resources.cameraTexture);
                resources.previewTexture=new SurfaceTexture(false);
                resources.previewTexture.setDefaultBufferSize(videoWidth,videoHeight);
                resources.previewSurface=new Surface(resources.previewTexture);
                EncoderSelection selection=encoder(tuple.videoMode());
                resources.encoder=MediaCodec.createByCodecName(selection.name());
                MediaFormat format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                        videoWidth,videoHeight);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                format.setInteger(MediaFormat.KEY_BIT_RATE,selection.bitrate());
                format.setInteger(MediaFormat.KEY_FRAME_RATE,tuple.videoMode().framesPerSecond());
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
                resources.encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
                resources.encoderSurface=resources.encoder.createInputSurface();
                resources.previewEgl=window(resources.previewSurface);
                resources.encoderEgl=window(resources.encoderSurface);
                resources.image=ImageReader.newInstance(imageWidth,imageHeight,ImageFormat.JPEG,2);
                resources.outputs=List.of(new OutputConfiguration(resources.cameraSurface),
                        new OutputConfiguration(resources.image.getSurface()));
                return resources;
            } catch(MediaCodec.CodecException error) {
                requireCleanup(resources);
                if(error.isTransient()||error.isRecoverable()) {
                    throw ProbeFailure.transientFailure("encoder:"+
                            error.getDiagnosticInfo(),error);
                }
                throw new TupleRejected("encoder:"+error.getDiagnosticInfo());
            } catch(EglUnavailable | ProbeFailure error) {
                requireCleanup(resources);
                throw error;
            } catch(IllegalArgumentException error) {
                requireCleanup(resources);
                throw new TupleRejected("tuple:"+error.getClass().getSimpleName());}
            catch(IOException error) {
                requireCleanup(resources);
                throw ProbeFailure.transientFailure("encoder:io",error);}
            catch(OutOfMemoryError error) {
                requireCleanup(resources);
                throw ProbeFailure.transientFailure("egl_tuple:out_of_memory",error);}
            catch(RuntimeException error) {
                requireCleanup(resources);
                throw ProbeFailure.global("egl_tuple:"+error.getClass().getSimpleName(),error);}
        }

        private void requireCleanup(TupleResources resources)throws ProbeFailure {
            ProbeFailure cleanup=releaseOnThread(resources);
            if(cleanup!=null) {
                throw cleanup;
            }
        }

        private EGLSurface window(Surface surface)throws TupleRejected,ProbeFailure {
            EGLSurface value=EGL14.eglCreateWindowSurface(display,config,surface,
                    new int[]{EGL14.EGL_NONE},0);
            if(value!=EGL14.EGL_NO_SURFACE) {
                return value;
            }
            int error=EGL14.eglGetError();
            if(isTemporaryAllocationFailure(error)) {
                throw ProbeFailure.transientFailure("egl_window:allocation",null);
            }
            throw new TupleRejected("egl_window:"+error);
        }

        private ProbeFailure release(TupleResources resources) {
            try {
                return call(()->releaseOnThread(resources));
            } catch(Exception error) {
                return ProbeFailure.transientFailure(
                    "egl_release:"+error.getClass().getSimpleName(),error);}
        }

        private ProbeFailure releaseOnThread(TupleResources resources) {
            ProbeFailure failure=null;
            EGLSurface preview=resources.previewEgl;
            resources.previewEgl=EGL14.EGL_NO_SURFACE;
            failure=firstFailure(failure,destroySurface(preview,"preview_surface"));
            EGLSurface encoder=resources.encoderEgl;
            resources.encoderEgl=EGL14.EGL_NO_SURFACE;
            failure=firstFailure(failure,destroySurface(encoder,"encoder_surface"));
            if(resources.cameraSurface!=null) {
                Surface value=resources.cameraSurface;
                resources.cameraSurface=null;
                failure=firstFailure(failure,cleanup("camera_surface",value::release));
            }
            if(resources.cameraTexture!=null) {
                SurfaceTexture value=resources.cameraTexture;
                resources.cameraTexture=null;
                failure=firstFailure(failure,cleanup("camera_texture",value::release));
            }
            int texture=resources.cameraTextureId;
            resources.cameraTextureId=0;
            if(texture!=0) {
                failure=firstFailure(failure,deleteTexture(texture));
            }
            if(resources.previewSurface!=null) {
                Surface value=resources.previewSurface;
                resources.previewSurface=null;
                failure=firstFailure(failure,cleanup("preview_surface",value::release));
            }
            if(resources.previewTexture!=null) {
                SurfaceTexture value=resources.previewTexture;
                resources.previewTexture=null;
                failure=firstFailure(failure,cleanup("preview_texture",value::release));
            }
            if(resources.encoderSurface!=null) {
                Surface value=resources.encoderSurface;
                resources.encoderSurface=null;
                failure=firstFailure(failure,cleanup("encoder_surface",value::release));
            }
            if(resources.encoder!=null) {
                MediaCodec value=resources.encoder;
                resources.encoder=null;
                failure=firstFailure(failure,cleanup("encoder",value::release));
            }
            if(resources.image!=null) {
                ImageReader value=resources.image;
                resources.image=null;
                failure=firstFailure(failure,cleanup("image_reader",value::close));
            }
            resources.outputs=null;
            return failure;
        }
        private ProbeFailure destroySurface(EGLSurface surface,String name) {
            if(surface==null||surface==EGL14.EGL_NO_SURFACE) {
                return null;
            }
            try {
                return EGL14.eglDestroySurface(display,surface)?null:
                        ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+name+":"+
                                EGL14.eglGetError(),null);
            } catch(RuntimeException error) {
                return ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+name+":"+
                        error.getClass().getSimpleName(),error);
            }
        }

        private ProbeFailure deleteTexture(int texture) {
            try {
                clearGlErrors();
                GLES20.glDeleteTextures(1,new int[]{texture},0);
                int error=GLES20.glGetError();
                return error==GLES20.GL_NO_ERROR?null:
                        ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+"texture:"+error,null);
            } catch(RuntimeException error) {
                return ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+"texture:"+
                        error.getClass().getSimpleName(),error);
            }
        }

        private static ProbeFailure cleanup(String name,Runnable action) {
            try {
                action.run();
                return null;
            } catch(RuntimeException error) {
                return ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+name+":"+
                        error.getClass().getSimpleName(),error);
            }
        }

        private ProbeFailure close() {
            ProbeFailure cleanup;
            try {
                cleanup=call(this::closeOnThread);
            } catch(Exception error) {
                cleanup=ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+
                        error.getClass().getSimpleName(),error);
            }
            return firstFailure(cleanup,stop());
        }

        private ProbeFailure closeOnThread() {
            ProbeFailure failure=null;
            boolean releaseThread=initialized;
            if(display!=EGL14.EGL_NO_DISPLAY&&initialized) {
                failure=firstFailure(failure,clearCurrentContext());
                failure=firstFailure(failure,destroyPbuffer());
                failure=firstFailure(failure,destroyContext());
                failure=firstFailure(failure,terminateDisplay());
            }
            display=EGL14.EGL_NO_DISPLAY;
            if(!releaseThread) {
                return failure;
            }
            initialized=false;
            return firstFailure(failure,releaseEglThread());
        }

        private ProbeFailure clearCurrentContext() {
            try {
                if(!EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)) {
                    return ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+
                            "make_current:"+EGL14.eglGetError(),null);
                }
            } catch(RuntimeException error) {
                return ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+"make_current:"+
                        error.getClass().getSimpleName(),error);
            }
            return null;
        }

        private ProbeFailure destroyPbuffer() {
            EGLSurface surface=pbuffer;
            pbuffer=EGL14.EGL_NO_SURFACE;
            return destroySurface(surface,"pbuffer");
        }

        private ProbeFailure destroyContext() {
            if(context==EGL14.EGL_NO_CONTEXT) {
                return null;
            }
            EGLContext value=context;
            context=EGL14.EGL_NO_CONTEXT;
            try {
                return EGL14.eglDestroyContext(display,value)?null:
                        ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+"context:"+
                                EGL14.eglGetError(),null);
            } catch(RuntimeException error) {
                return ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+"context:"+
                        error.getClass().getSimpleName(),error);
            }
        }

        private ProbeFailure terminateDisplay() {
            EGLDisplay value=display;
            display=EGL14.EGL_NO_DISPLAY;
            initialized=false;
            try {
                return EGL14.eglTerminate(value)?null:
                        ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+"terminate:"+
                                EGL14.eglGetError(),null);
            } catch(RuntimeException error) {
                return ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+"terminate:"+
                        error.getClass().getSimpleName(),error);
            }
        }

        private ProbeFailure releaseEglThread() {
            try {
                return EGL14.eglReleaseThread()?null:
                        ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+"release_thread:"+
                                EGL14.eglGetError(),null);
            } catch(RuntimeException error) {
                return ProbeFailure.transientFailure(EGL_CLEANUP_PREFIX+"release_thread:"+
                        error.getClass().getSimpleName(),error);
            }
        }
        private ProbeFailure stop() {
            thread.quitSafely();
            try {
                thread.join(RELEASE_TIMEOUT_MILLIS);
                if(thread.isAlive()) {
                    thread.quit();
                    thread.join(RELEASE_TIMEOUT_MILLIS);
                }
            } catch(InterruptedException error) {
                Thread.currentThread().interrupt();
                return ProbeFailure.transientFailure("egl_thread:join_interrupted",error);
            }
            return thread.isAlive()
                    ?ProbeFailure.transientFailure("egl_thread:join_timeout",null):null;
        }

        private <T> T call(Callable<T> action)throws EglUnavailable,ProbeFailure,TupleRejected {
            FutureTask<T> task=new FutureTask<>(action);
            if(!handler.post(task)) {
                throw ProbeFailure.transientFailure("egl_thread:stopped",null);
            }
            try {
                return task.get(EGL_TIMEOUT_MILLIS,TimeUnit.MILLISECONDS);
            } catch(TimeoutException error) {
                task.cancel(true);
                throw ProbeFailure.transientFailure("egl_thread:timeout",error);
            } catch(InterruptedException error) {
                task.cancel(true);
                Thread.currentThread().interrupt();
                throw ProbeFailure.transientFailure("egl_thread:interrupted",error);
            } catch(java.util.concurrent.CancellationException error) {
                throw ProbeFailure.transientFailure("egl_thread:cancelled",error);
            } catch(java.util.concurrent.ExecutionException error) {
                Throwable cause=error.getCause();
                if(cause instanceof EglUnavailable value) {
                    throw value;
                }
                if(cause instanceof TupleRejected value) {
                    throw value;
                }
                if(cause instanceof ProbeFailure value) {
                    throw value;
                }
                if(cause instanceof OutOfMemoryError value) {
                    throw ProbeFailure.transientFailure("egl_thread:out_of_memory",value);
                }
                throw ProbeFailure.global("egl_thread:"+
                        cause.getClass().getSimpleName(),cause);
            }
        }

        private static void clearGlErrors() {
            while(GLES20.glGetError()!=GLES20.GL_NO_ERROR) {
                // Drain stale GL errors before checking the operation under test.
            }
        }

        private static EncoderSelection encoder(VideoMode mode)throws TupleRejected {
            int width=mode.resolution().actual().width();
            int height=mode.resolution().actual().height();
            int framesPerSecond=mode.framesPerSecond();
            for(MediaCodecInfo codec:new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
                Optional<EncoderSelection> selection=supportedEncoder(codec,width,height,
                        framesPerSecond);
                if(selection.isPresent()) {
                    return selection.get();
                }
            }
            throw new TupleRejected("encoder:no_matching_h264");
        }

        private static Optional<EncoderSelection> supportedEncoder(MediaCodecInfo codec,
                int width,int height,int framesPerSecond) {
            if(!codec.isEncoder()) {
                return Optional.empty();
            }
            MediaCodecInfo.CodecCapabilities capabilities;
            try {
                capabilities=codec.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);
            } catch(IllegalArgumentException error) {
                return Optional.empty();
            }
            MediaCodecInfo.VideoCapabilities video=capabilities.getVideoCapabilities();
            if(video==null) {
                return Optional.empty();
            }
            try {
                if(!video.areSizeAndRateSupported(width,height,framesPerSecond)) {
                    return Optional.empty();
                }
            } catch(IllegalArgumentException error) {
                return Optional.empty();
            }
            Range<Integer> bitrates=video.getBitrateRange();
            return Optional.of(new EncoderSelection(codec.getName(),
                    bitrate(bitrates,width,height,framesPerSecond)));
        }

        private static int bitrate(Range<Integer> bitrates,int width,int height,
                int framesPerSecond) {
            long target=(long)width*height*framesPerSecond/4;
            long positiveTarget=target<1L?1L:target;
            return clampBitrate(bitrates,positiveTarget);
        }

        private static int clampBitrate(Range<Integer> bitrates,long target) {
            int lower=bitrates.getLower();
            int upper=bitrates.getUpper();
            if(target<lower) {
                return lower;
            }
            if(target>upper) {
                return upper;
            }
            return (int) target;
        }

        private record EncoderSelection(String name,int bitrate) { }
    }
    private static final class TupleResources {
        int cameraTextureId;
        SurfaceTexture cameraTexture;
        SurfaceTexture previewTexture;
        Surface cameraSurface;
        Surface previewSurface;
        Surface encoderSurface;
        MediaCodec encoder;
        ImageReader image;
        EGLSurface previewEgl;
        EGLSurface encoderEgl;
        List<OutputConfiguration> outputs;

        List<OutputConfiguration> outputs() {
            return outputs;
        }
    }
    private static final class EglUnavailable extends Exception {
        private EglUnavailable(String message) {
            super(message);
        }
    }

    private static final class TupleRejected extends Exception {
        private TupleRejected(String message) {
            super(message);
        }
    }

    private static final class ProbeFailure extends Exception{
        private final Completion completion;
        private final String detail;

        private ProbeFailure(Completion completion,String detail,Throwable cause) {
            super(detail,cause);
            this.completion=completion;
            this.detail=detail;
        }

        private static ProbeFailure transientFailure(String detail,Throwable cause) {
            return new ProbeFailure(Completion.INCOMPLETE_TRANSIENT,detail,cause);
        }

        private static ProbeFailure global(String detail,Throwable cause) {
            return new ProbeFailure(Completion.INCOMPLETE_GLOBAL,detail,cause);
        }

        private static ProbeFailure blocked(String detail,Throwable cause) {
            return new ProbeFailure(Completion.BLOCKED_EXTERNAL,detail,cause);
        }

        private Completion completion() {
            return completion;
        }

        private String detail() {
            return detail;
        }
    }

    private static String required(String value,String name) {
        if(value==null||value.isBlank()) {
            throw new IllegalArgumentException(name+" required");
        }
        return value;
    }
}
