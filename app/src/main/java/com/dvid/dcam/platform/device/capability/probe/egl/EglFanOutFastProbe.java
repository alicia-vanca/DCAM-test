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
    private static final long OPEN_TIMEOUT_MILLIS=5_000, SESSION_TIMEOUT_MILLIS=3_000,
            RELEASE_TIMEOUT_MILLIS=2_000, EGL_TIMEOUT_MILLIS=5_000;
    private static final String CAMERA_SOURCES="SurfaceTexture|ImageReader";
    private static final String DOWNSTREAM_SOURCES="SurfaceTexture+MediaCodec";
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
        EglWorker egl;
        try { egl=EglWorker.open(cameraId); }
        catch (EglUnavailable unavailable) {
            Result result=result(cameraId,PipelineAvailability.UNAVAILABLE,
                    Completion.PIPELINE_UNAVAILABLE,List.of(),List.of(),true,
                    unavailable.getMessage(),started);
            logComplete(result,imageProfileCount); return result;
        } catch (ProbeFailure failure) {
            Result result=incomplete(cameraId,List.of(),List.of(),false,failure,started);
            logFailure(cameraId,"egl_init",failure); logComplete(result,imageProfileCount); return result;
        }
        OpenedCamera camera=null;Result result=null;
        ProbeFailure cameraCleanup=null,eglCleanup=null;
        try {
            logger.info(prefix(cameraId)+" stage=egl_init result=available"
                    +" cameraOutputCount=2 downstreamSurfaceCount=2"
                    +" cameraSurfaceClasses="+CAMERA_SOURCES
                    +" downstreamSurfaceClasses="+DOWNSTREAM_SOURCES);
            try { camera=OpenedCamera.open(context,cameraId); }
            catch (ProbeFailure failure) {
                result=incomplete(cameraId,List.of(),List.of(),false,failure,started);
                logFailure(cameraId,"open",failure);
            }
            if(result==null) {
                OpenedCamera openedCamera=camera;
                try {
                    result=runMatrix(cameraId,videos,images,
                            (tuple,attempt,confirmation)->query(openedCamera,egl,tuple),
                            logger,started);
                } catch(RuntimeException error) {
                    ProbeFailure failure=ProbeFailure.global(
                            "probe:"+error.getClass().getSimpleName(),error);
                    result=incomplete(cameraId,List.of(),List.of(),false,failure,started);
                    logFailure(cameraId,"probe",failure);
                }
            }
        } finally {
            if(camera!=null)cameraCleanup=camera.release();
            eglCleanup=egl.close();
            ProbeFailure cleanup=firstFailure(cameraCleanup,eglCleanup);
            if(result!=null) {
                if(cleanup!=null)result=incomplete(cameraId,
                        result.evidence().rawFastCandidates(),result.attempts(),
                        false,cleanup,started);
                else result=new Result(result.evidence(),result.completion(),result.attempts(),
                        true,result.elapsedMillis(),result.detail());
            }
        }
        if(cameraCleanup!=null)logFailure(cameraId,"camera_cleanup",cameraCleanup);
        if(eglCleanup!=null)logFailure(cameraId,"egl_cleanup",eglCleanup);
        logComplete(result,imageProfileCount);
        return result;
    }

    static Result runMatrix(CameraId cameraId, Collection<VideoMode> videoModes,
            Collection<ImageMode> imageModes, TupleQuery query, Logger logger, long started) {
        TreeSet<VideoMode> videos=sorted(videoModes,"videoModes");
        TreeSet<ImageMode> images=sorted(imageModes,"imageModes");
        List<CaptureModeTuple> universe=new ArrayList<>();
        for(VideoMode video:videos) for(ImageMode image:images)
            universe.add(new CaptureModeTuple(video,image));
        Map<CaptureModeTuple,AttemptResult> states=new TreeMap<>();
        List<Attempt> attempts=new ArrayList<>();
        for(CaptureModeTuple tuple:universe) {
            Result stop=attempt(cameraId,tuple,1,"initial",query,logger,states,attempts,started);
            if(stop!=null)return stop;
        }
        Set<CaptureModeTuple> confirmed=new HashSet<>();
        for(VideoMode video:videos) {
            List<CaptureModeTuple> row=universe.stream().filter(t->t.videoMode().equals(video))
                    .collect(java.util.stream.Collectors.toList());
            Result stop=confirm(cameraId,row,"fps_row",query,logger,states,attempts,confirmed,started);
            if(stop!=null)return stop;
        }
        TreeSet<StandardResolution> resolutions=new TreeSet<>();
        videos.forEach(v->resolutions.add(v.resolution()));
        for(StandardResolution resolution:resolutions) {
            List<CaptureModeTuple> mode=universe.stream()
                    .filter(t->t.videoMode().resolution().equals(resolution))
                    .collect(java.util.stream.Collectors.toList());
            Result stop=confirm(cameraId,mode,"video_mode",query,logger,states,attempts,confirmed,started);
            if(stop!=null)return stop;
        }
        return result(cameraId,PipelineAvailability.AVAILABLE,Completion.COMPLETE,
                supported(cameraId,states),attempts,false,"matrix_complete",started);
    }

    private static Result confirm(CameraId cameraId,List<CaptureModeTuple> tuples,String reason,
            TupleQuery query,Logger logger,Map<CaptureModeTuple,AttemptResult> states,
            List<Attempt> attempts,Set<CaptureModeTuple> confirmed,long started) {
        if(tuples.isEmpty()||!tuples.stream().allMatch(t->states.get(t)==AttemptResult.REJECTED))
            return null;
        for(CaptureModeTuple tuple:tuples) {
            if(states.get(tuple)!=AttemptResult.REJECTED||!confirmed.add(tuple))continue;
            Result stop=attempt(cameraId,tuple,2,reason,query,logger,states,attempts,started);
            if(stop!=null)return stop;
        }
        return null;
    }

    private static Result attempt(CameraId cameraId,CaptureModeTuple tuple,int number,String reason,
            TupleQuery query,Logger logger,Map<CaptureModeTuple,AttemptResult> states,
            List<Attempt> attempts,long started) {
        long tick=System.nanoTime();
        Decision decision;
        try {
            decision=Objects.requireNonNull(query.query(tuple,number,reason),"decision");
        } catch(RuntimeException error) {
            decision=Decision.unbuilt(AttemptResult.INCOMPLETE_GLOBAL,
                    "query:"+error.getClass().getSimpleName());
        }
        Attempt attempt=new Attempt(tuple,decision.result(),number,reason,
                decision.cameraOutputCount(),decision.downstreamSurfaceCount(),
                decision.cameraSurfaceClasses(),decision.downstreamSurfaceClasses(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-tick),decision.detail());
        attempts.add(attempt);

        if(decision.result()==AttemptResult.SUPPORTED||decision.result()==AttemptResult.REJECTED) {
            states.put(tuple,decision.result()); return null;
        }
        if(decision.result()==AttemptResult.PIPELINE_UNAVAILABLE)
            return result(cameraId,PipelineAvailability.UNAVAILABLE,Completion.PIPELINE_UNAVAILABLE,
                    List.of(),attempts,false,decision.detail(),started);
        ProbeFailure failure=new ProbeFailure(completion(decision.result()),decision.detail(),null);
        return incomplete(cameraId,supported(cameraId,states),attempts,false,failure,started);
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
            supported=Build.VERSION.SDK_INT>=Build.VERSION_CODES.P
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
        states.forEach((tuple,state)->{if(state==AttemptResult.SUPPORTED)
            result.add(CandidateKey.forTuple(cameraId,VideoCodec.H264,PIPELINE_ID,tuple));});
        return List.copyOf(result);
    }

    private static <T extends Comparable<? super T>> TreeSet<T> sorted(Collection<T> values,String name) {
        TreeSet<T> result=new TreeSet<>();
        for(T value:Objects.requireNonNull(values,name))result.add(Objects.requireNonNull(value,name));
        return result;
    }

    private static Result incomplete(CameraId cameraId,Collection<CandidateKey> candidates,
            Collection<Attempt> attempts,boolean cleanup,ProbeFailure failure,long started) {
        return result(cameraId,PipelineAvailability.UNKNOWN,failure.completion(),candidates,
                attempts,cleanup,failure.detail(),started);
    }

    private static Result result(CameraId cameraId,PipelineAvailability availability,
            Completion completion,Collection<CandidateKey> candidates,Collection<Attempt> attempts,
            boolean cleanup,String detail,long started) {
        PipelineEvidence evidence=new PipelineEvidence(cameraId,VideoCodec.H264,PIPELINE_ID,
                availability,candidates,List.of());
        return new Result(evidence,completion,List.copyOf(attempts),cleanup,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),required(detail,"detail"));
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
    private static ProbeFailure cameraStateFailure(int code) {
        return switch(code) {
            case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED->ProbeFailure.blocked("camera_state:disabled",null);
            case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE,
                    CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE->
                    ProbeFailure.transientFailure("camera_state:error_"+code,null);
            default->ProbeFailure.global("camera_state:error_"+code,null);
        };
    }

    static boolean isTemporaryAllocationFailure(int error) {
        return error==EGL14.EGL_BAD_ALLOC||error==GLES20.GL_OUT_OF_MEMORY;
    }
    private static void throwEglSetupFailure(String stage,int error)
            throws EglUnavailable,ProbeFailure {
        if(isTemporaryAllocationFailure(error))
            throw ProbeFailure.transientFailure(stage+":allocation",null);
        throw new EglUnavailable(stage+":"+error);
    }
    private static void throwGlSetupFailure(String stage,int error)
            throws EglUnavailable,ProbeFailure {
        if(isTemporaryAllocationFailure(error))
            throw ProbeFailure.transientFailure(stage+":allocation",null);
        throw new EglUnavailable(stage+":"+error);
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
            confirmation=required(confirmation,"confirmation");
            cameraSurfaceClasses=required(cameraSurfaceClasses,"cameraSurfaceClasses");
            downstreamSurfaceClasses=required(downstreamSurfaceClasses,"downstreamSurfaceClasses");
            detail=required(detail,"detail"); }
    }
    public record Result(PipelineEvidence evidence,Completion completion,List<Attempt> attempts,
            boolean cleanupComplete,long elapsedMillis,String detail) {
        public Result { Objects.requireNonNull(evidence);Objects.requireNonNull(completion);
            attempts=List.copyOf(attempts);if(elapsedMillis<0)throw new IllegalArgumentException();
            detail=required(detail,"detail"); }
        public boolean complete(){return completion==Completion.COMPLETE;}
    }
    @FunctionalInterface interface TupleQuery { Decision query(CaptureModeTuple tuple,int attempt,String confirmation); }
    record Decision(AttemptResult result,int cameraOutputCount,int downstreamSurfaceCount,
            String cameraSurfaceClasses,String downstreamSurfaceClasses,String detail) {
        Decision { Objects.requireNonNull(result);detail=required(detail,"detail");
            cameraSurfaceClasses=required(cameraSurfaceClasses,"cameraSurfaceClasses");
            downstreamSurfaceClasses=required(downstreamSurfaceClasses,"downstreamSurfaceClasses"); }
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
        synchronized boolean accept(T candidate){if(abandoned)return false;value=candidate;return true;}
        synchronized T abandon(){abandoned=true;T owned=value;value=null;return owned;}
        synchronized T value(){return value;}
        synchronized boolean abandoned(){return abandoned;}
    }

    private static final class OpenedCamera {
        final CameraDevice camera;final HandlerThread thread;final Handler handler;
        final AtomicReference<ProbeFailure> failure;final CountDownLatch closed;
        OpenedCamera(CameraDevice camera,HandlerThread thread,Handler handler,
                AtomicReference<ProbeFailure> failure,CountDownLatch closed){
            this.camera=camera;this.thread=thread;this.handler=handler;
            this.failure=failure;this.closed=closed;}
        static OpenedCamera open(Context context,CameraId id)throws ProbeFailure{
            CameraManager manager=context.getSystemService(CameraManager.class);
            if(manager==null)throw ProbeFailure.global("open:no_manager",null);
            HandlerThread thread=new HandlerThread("dcam-egl-fast-camera-"+id);thread.start();
            Handler handler=new Handler(thread.getLooper());
            CountDownLatch opened=new CountDownLatch(1),closed=new CountDownLatch(1);
            OpenGuard<CameraDevice> guard=new OpenGuard<>();
            AtomicReference<ProbeFailure> failure=new AtomicReference<>();
            boolean requestSubmitted=false;
            try{
                manager.openCamera(id.value(),new CameraDevice.StateCallback(){
                    public void onOpened(CameraDevice camera){
                        if(!guard.accept(camera))camera.close();opened.countDown();}
                    public void onDisconnected(CameraDevice camera){
                        failure.compareAndSet(null,ProbeFailure.transientFailure(
                                "camera_state:disconnected",null));camera.close();opened.countDown();}
                    public void onError(CameraDevice camera,int code){
                        failure.compareAndSet(null,cameraStateFailure(code));
                        camera.close();opened.countDown();}
                    public void onClosed(CameraDevice camera){
                        closed.countDown();if(guard.abandoned())thread.quitSafely();}
                },handler);
                requestSubmitted=true;
                if(!opened.await(OPEN_TIMEOUT_MILLIS,TimeUnit.MILLISECONDS)){
                    ProbeFailure cleanup=abandonOpen(guard,closed,thread);
                    if(cleanup!=null)throw cleanup;
                    throw ProbeFailure.transientFailure("open:timeout",null);
                }
                CameraDevice openedCamera=guard.value();ProbeFailure openFailure=failure.get();
                if(openedCamera==null||openFailure!=null){
                    ProbeFailure cleanup=abandonOpen(guard,closed,thread);
                    if(cleanup!=null)throw cleanup;
                    throw openFailure==null?ProbeFailure.global("open:unknown",null):openFailure;
                }
                return new OpenedCamera(openedCamera,thread,handler,failure,closed);
            }catch(SecurityException error){
                ProbeFailure cleanup=requestSubmitted?abandonOpen(guard,closed,thread):
                        stopThread(thread,"open_thread");
                if(cleanup!=null)throw cleanup;
                throw ProbeFailure.blocked("open:permission",error);
            }catch(CameraAccessException error){
                ProbeFailure cleanup=requestSubmitted?abandonOpen(guard,closed,thread):
                        stopThread(thread,"open_thread");
                if(cleanup!=null)throw cleanup;
                throw cameraFailure("open",error);
            }catch(InterruptedException error){
                ProbeFailure cleanup=requestSubmitted?abandonOpen(guard,closed,thread):
                        stopThread(thread,"open_thread");
                Thread.currentThread().interrupt();if(cleanup!=null)throw cleanup;
                throw ProbeFailure.transientFailure("open:interrupted",error);
            }catch(RuntimeException error){
                ProbeFailure cleanup=requestSubmitted?abandonOpen(guard,closed,thread):
                        stopThread(thread,"open_thread");
                if(cleanup!=null)throw cleanup;
                throw ProbeFailure.global("open:"+error.getClass().getSimpleName(),error);
            }
        }
        static ProbeFailure abandonOpen(OpenGuard<CameraDevice> guard,CountDownLatch closed,
                HandlerThread thread){
            ProbeFailure failure=null;CameraDevice owned=guard.abandon();
            if(owned!=null)try{owned.close();}catch(RuntimeException error){failure=
                    ProbeFailure.transientFailure("open_cleanup:"+
                            error.getClass().getSimpleName(),error);}
            if(closed.getCount()==0)
                failure=firstFailure(failure,stopThread(thread,"open_thread"));
            return failure;
        }
        boolean supports(List<OutputConfiguration> outputs)throws CameraAccessException,ProbeFailure{
            ensureAvailable();SessionConfiguration config=new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,outputs,command->handler.post(command),
                    new CameraCaptureSession.StateCallback(){
                        public void onConfigured(CameraCaptureSession session){}
                        public void onConfigureFailed(CameraCaptureSession session){}});
            return camera.isSessionConfigurationSupported(config);
        }
        boolean configures(List<OutputConfiguration> outputs)throws ProbeFailure{
            CountDownLatch done=new CountDownLatch(1),sessionClosed=new CountDownLatch(1);
            AtomicReference<Boolean> ok=new AtomicReference<>();
            AtomicReference<CameraCaptureSession> session=new AtomicReference<>();
            try{camera.createCaptureSessionByOutputConfigurations(outputs,
                    new CameraCaptureSession.StateCallback(){
                        public void onConfigured(CameraCaptureSession value){
                            session.set(value);ok.set(true);done.countDown();}
                        public void onConfigureFailed(CameraCaptureSession value){
                            session.set(value);ok.set(false);value.close();done.countDown();}
                        public void onClosed(CameraCaptureSession value){sessionClosed.countDown();}
                    },handler);
                if(!done.await(SESSION_TIMEOUT_MILLIS,TimeUnit.MILLISECONDS)){
                    CameraCaptureSession value=session.get();if(value!=null)value.close();
                    throw ProbeFailure.transientFailure("session:timeout",null);}
                if(Boolean.TRUE.equals(ok.get()))session.get().close();
                if(session.get()!=null&&
                        !sessionClosed.await(RELEASE_TIMEOUT_MILLIS,TimeUnit.MILLISECONDS))
                    throw ProbeFailure.transientFailure("session:release_timeout",null);
                ensureAvailable();return Boolean.TRUE.equals(ok.get());
            }catch(CameraAccessException error){throw cameraFailure("session",error);}
            catch(InterruptedException error){Thread.currentThread().interrupt();
                throw ProbeFailure.transientFailure("session:interrupted",error);}
        }
        void ensureAvailable()throws ProbeFailure{if(failure.get()!=null)throw failure.get();}
        ProbeFailure release(){
            ProbeFailure failure=null;
            try{camera.close();if(!closed.await(RELEASE_TIMEOUT_MILLIS,TimeUnit.MILLISECONDS))
                    failure=ProbeFailure.transientFailure("camera_release:timeout",null);}
            catch(InterruptedException error){Thread.currentThread().interrupt();
                failure=ProbeFailure.transientFailure("camera_release:interrupted",error);}
            catch(RuntimeException error){failure=ProbeFailure.transientFailure(
                    "camera_release:"+error.getClass().getSimpleName(),error);}
            return firstFailure(failure,stopThread(thread,"camera_thread"));
        }
        static ProbeFailure stopThread(HandlerThread thread,String detail){
            thread.quitSafely();
            try{thread.join(RELEASE_TIMEOUT_MILLIS);if(thread.isAlive()){
                    thread.quit();thread.join(RELEASE_TIMEOUT_MILLIS);}}
            catch(InterruptedException error){Thread.currentThread().interrupt();
                return ProbeFailure.transientFailure(detail+":interrupted",error);}
            return thread.isAlive()?ProbeFailure.transientFailure(detail+":timeout",null):null;
        }
    }
    private static final class EglWorker {
        final HandlerThread thread;final Handler handler;
        EGLDisplay display=EGL14.EGL_NO_DISPLAY;EGLConfig config;boolean initialized;
        EGLContext context=EGL14.EGL_NO_CONTEXT;EGLSurface pbuffer=EGL14.EGL_NO_SURFACE;
        EglWorker(HandlerThread thread,Handler handler){this.thread=thread;this.handler=handler;}
        static EglWorker open(CameraId id)throws EglUnavailable,ProbeFailure{
            HandlerThread thread=new HandlerThread("dcam-egl-fast-"+id);thread.start();
            EglWorker worker=new EglWorker(thread,new Handler(thread.getLooper()));
            try{worker.call(()->{worker.initialize();return null;});return worker;}
            catch(EglUnavailable error){ProbeFailure cleanup=worker.close();
                if(cleanup!=null)throw cleanup;throw error;}
            catch(ProbeFailure error){ProbeFailure cleanup=worker.close();
                if(cleanup!=null)throw cleanup;throw error;}
            catch(TupleRejected error){ProbeFailure cleanup=worker.close();
                if(cleanup!=null)throw cleanup;
                throw ProbeFailure.global("egl_init:unexpected_tuple",error);}
            catch(RuntimeException error){ProbeFailure cleanup=worker.close();
                if(cleanup!=null)throw cleanup;
                throw ProbeFailure.global("egl_init:"+error.getClass().getSimpleName(),error);}
            catch(OutOfMemoryError error){ProbeFailure cleanup=worker.close();
                if(cleanup!=null)throw cleanup;
                throw ProbeFailure.transientFailure("egl_init:out_of_memory",error);}
        }
        void initialize()throws EglUnavailable,ProbeFailure{
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if(display==EGL14.EGL_NO_DISPLAY)
                throwEglSetupFailure("egl_display",EGL14.eglGetError());
            int[] version=new int[2];
            if(!EGL14.eglInitialize(display,version,0,version,1))
                throwEglSetupFailure("egl_initialize",EGL14.eglGetError());
            initialized=true;
            int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
                    EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                    EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT|EGL14.EGL_PBUFFER_BIT,
                    EGLExt.EGL_RECORDABLE_ANDROID,1,EGL14.EGL_NONE};
            EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
            if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0))
                throwEglSetupFailure("egl_choose_config",EGL14.eglGetError());
            if(count[0]==0)throw new EglUnavailable("egl:no_recordable_config");
            config=configs[0];
            context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            if(context==EGL14.EGL_NO_CONTEXT)
                throwEglSetupFailure("egl_context",EGL14.eglGetError());
            pbuffer=EGL14.eglCreatePbufferSurface(display,config,
                    new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
            if(pbuffer==EGL14.EGL_NO_SURFACE)
                throwEglSetupFailure("egl_pbuffer",EGL14.eglGetError());
            if(!EGL14.eglMakeCurrent(display,pbuffer,pbuffer,context))
                throwEglSetupFailure("egl_make_current",EGL14.eglGetError());
        }
        TupleResources create(CaptureModeTuple tuple)throws TupleRejected,EglUnavailable,ProbeFailure{
            return call(()->createOnThread(tuple));
        }
        TupleResources createOnThread(CaptureModeTuple tuple)
                throws TupleRejected,EglUnavailable,ProbeFailure{
            int videoWidth=tuple.videoMode().resolution().actual().width();
            int videoHeight=tuple.videoMode().resolution().actual().height();
            int imageWidth=tuple.imageMode().resolution().actual().width();
            int imageHeight=tuple.imageMode().resolution().actual().height();
            TupleResources resources=new TupleResources();
            try{
                clearGlErrors();int[] texture=new int[1];GLES20.glGenTextures(1,texture,0);
                int glError=GLES20.glGetError();
                if(texture[0]==0){
                    if(glError==GLES20.GL_NO_ERROR)
                        throw new EglUnavailable("egl:no_external_texture");
                    throwGlSetupFailure("gl_external_texture",glError);
                }
                resources.cameraTextureId=texture[0];
                if(glError!=GLES20.GL_NO_ERROR)
                    throwGlSetupFailure("gl_external_texture",glError);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture[0]);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
                glError=GLES20.glGetError();
                if(glError!=GLES20.GL_NO_ERROR)
                    throwGlSetupFailure("gl_external_texture",glError);
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
            }catch(MediaCodec.CodecException error){
                requireCleanup(resources);
                if(error.isTransient()||error.isRecoverable())
                    throw ProbeFailure.transientFailure("encoder:"+
                            error.getDiagnosticInfo(),error);
                throw new TupleRejected("encoder:"+error.getDiagnosticInfo());
            }catch(EglUnavailable error){requireCleanup(resources);throw error;}
            catch(ProbeFailure error){requireCleanup(resources);throw error;}
            catch(IllegalArgumentException error){requireCleanup(resources);
                throw new TupleRejected("tuple:"+error.getClass().getSimpleName());}
            catch(IOException error){requireCleanup(resources);
                throw ProbeFailure.transientFailure("encoder:io",error);}
            catch(OutOfMemoryError error){requireCleanup(resources);
                throw ProbeFailure.transientFailure("egl_tuple:out_of_memory",error);}
            catch(RuntimeException error){requireCleanup(resources);
                throw ProbeFailure.global("egl_tuple:"+error.getClass().getSimpleName(),error);}
        }
        void requireCleanup(TupleResources resources)throws ProbeFailure{
            ProbeFailure cleanup=releaseOnThread(resources);if(cleanup!=null)throw cleanup;
        }
        EGLSurface window(Surface surface)throws TupleRejected,ProbeFailure{
            EGLSurface value=EGL14.eglCreateWindowSurface(display,config,surface,
                    new int[]{EGL14.EGL_NONE},0);
            if(value!=EGL14.EGL_NO_SURFACE)return value;
            int error=EGL14.eglGetError();
            if(isTemporaryAllocationFailure(error))
                throw ProbeFailure.transientFailure("egl_window:allocation",null);
            throw new TupleRejected("egl_window:"+error);
        }
        ProbeFailure release(TupleResources resources){
            try{return call(()->releaseOnThread(resources));}
            catch(Exception error){return ProbeFailure.transientFailure(
                    "egl_release:"+error.getClass().getSimpleName(),error);}
        }
        ProbeFailure releaseOnThread(TupleResources resources){
            ProbeFailure failure=null;
            EGLSurface preview=resources.previewEgl;
            resources.previewEgl=EGL14.EGL_NO_SURFACE;
            failure=firstFailure(failure,destroySurface(preview,"preview_surface"));
            EGLSurface encoder=resources.encoderEgl;
            resources.encoderEgl=EGL14.EGL_NO_SURFACE;
            failure=firstFailure(failure,destroySurface(encoder,"encoder_surface"));
            if(resources.cameraSurface!=null){
                Surface value=resources.cameraSurface;resources.cameraSurface=null;
                failure=firstFailure(failure,cleanup("camera_surface",value::release));
            }
            if(resources.cameraTexture!=null){
                SurfaceTexture value=resources.cameraTexture;resources.cameraTexture=null;
                failure=firstFailure(failure,cleanup("camera_texture",value::release));
            }
            int texture=resources.cameraTextureId;resources.cameraTextureId=0;
            if(texture!=0)failure=firstFailure(failure,deleteTexture(texture));
            if(resources.previewSurface!=null){
                Surface value=resources.previewSurface;resources.previewSurface=null;
                failure=firstFailure(failure,cleanup("preview_surface",value::release));
            }
            if(resources.previewTexture!=null){
                SurfaceTexture value=resources.previewTexture;resources.previewTexture=null;
                failure=firstFailure(failure,cleanup("preview_texture",value::release));
            }
            if(resources.encoderSurface!=null){
                Surface value=resources.encoderSurface;resources.encoderSurface=null;
                failure=firstFailure(failure,cleanup("encoder_surface",value::release));
            }
            if(resources.encoder!=null){
                MediaCodec value=resources.encoder;resources.encoder=null;
                failure=firstFailure(failure,cleanup("encoder",value::release));
            }
            if(resources.image!=null){
                ImageReader value=resources.image;resources.image=null;
                failure=firstFailure(failure,cleanup("image_reader",value::close));
            }
            resources.outputs=null;
            return failure;
        }
        ProbeFailure destroySurface(EGLSurface surface,String name){
            if(surface==null||surface==EGL14.EGL_NO_SURFACE)return null;
            try{return EGL14.eglDestroySurface(display,surface)?null:
                    ProbeFailure.transientFailure("egl_cleanup:"+name+":"+
                            EGL14.eglGetError(),null);}
            catch(RuntimeException error){return ProbeFailure.transientFailure(
                    "egl_cleanup:"+name+":"+error.getClass().getSimpleName(),error);}
        }
        ProbeFailure deleteTexture(int texture){
            try{clearGlErrors();GLES20.glDeleteTextures(1,new int[]{texture},0);
                int error=GLES20.glGetError();
                return error==GLES20.GL_NO_ERROR?null:
                        ProbeFailure.transientFailure("egl_cleanup:texture:"+error,null);}
            catch(RuntimeException error){return ProbeFailure.transientFailure(
                    "egl_cleanup:texture:"+error.getClass().getSimpleName(),error);}
        }
        static ProbeFailure cleanup(String name,Runnable action){
            try{action.run();return null;}
            catch(RuntimeException error){return ProbeFailure.transientFailure(
                    "egl_cleanup:"+name+":"+error.getClass().getSimpleName(),error);}
        }
        ProbeFailure close(){
            ProbeFailure cleanup;
            try{cleanup=call(this::closeOnThread);}
            catch(Exception error){cleanup=ProbeFailure.transientFailure(
                    "egl_cleanup:"+error.getClass().getSimpleName(),error);}
            return firstFailure(cleanup,stop());
        }
        ProbeFailure closeOnThread(){
            ProbeFailure failure=null;boolean releaseThread=initialized;
            if(display!=EGL14.EGL_NO_DISPLAY&&initialized){
                try{
                    if(!EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT))
                        failure=firstFailure(failure,ProbeFailure.transientFailure(
                                "egl_cleanup:make_current:"+EGL14.eglGetError(),null));
                }catch(RuntimeException error){
                    failure=firstFailure(failure,ProbeFailure.transientFailure(
                            "egl_cleanup:make_current:"+
                                    error.getClass().getSimpleName(),error));
                }
                EGLSurface surface=pbuffer;pbuffer=EGL14.EGL_NO_SURFACE;
                failure=firstFailure(failure,destroySurface(surface,"pbuffer"));
                if(context!=EGL14.EGL_NO_CONTEXT){
                    EGLContext value=context;context=EGL14.EGL_NO_CONTEXT;
                    try{
                        if(!EGL14.eglDestroyContext(display,value))
                            failure=firstFailure(failure,ProbeFailure.transientFailure(
                                    "egl_cleanup:context:"+EGL14.eglGetError(),null));
                    }catch(RuntimeException error){
                        failure=firstFailure(failure,ProbeFailure.transientFailure(
                                "egl_cleanup:context:"+
                                        error.getClass().getSimpleName(),error));
                    }
                }
                EGLDisplay value=display;display=EGL14.EGL_NO_DISPLAY;initialized=false;
                try{
                    if(!EGL14.eglTerminate(value))
                        failure=firstFailure(failure,ProbeFailure.transientFailure(
                                "egl_cleanup:terminate:"+EGL14.eglGetError(),null));
                }catch(RuntimeException error){
                    failure=firstFailure(failure,ProbeFailure.transientFailure(
                            "egl_cleanup:terminate:"+
                                    error.getClass().getSimpleName(),error));
                }
            }
            display=EGL14.EGL_NO_DISPLAY;
            if(!releaseThread)return failure;
            initialized=false;
            try{
                if(!EGL14.eglReleaseThread())
                    failure=firstFailure(failure,ProbeFailure.transientFailure(
                            "egl_cleanup:release_thread:"+EGL14.eglGetError(),null));
            }catch(RuntimeException error){
                failure=firstFailure(failure,ProbeFailure.transientFailure(
                        "egl_cleanup:release_thread:"+
                                error.getClass().getSimpleName(),error));
            }
            return failure;
        }
        ProbeFailure stop(){
            thread.quitSafely();
            try{
                thread.join(RELEASE_TIMEOUT_MILLIS);
                if(thread.isAlive()){
                    thread.quit();
                    thread.join(RELEASE_TIMEOUT_MILLIS);
                }
            }catch(InterruptedException error){
                Thread.currentThread().interrupt();
                return ProbeFailure.transientFailure("egl_thread:join_interrupted",error);
            }
            return thread.isAlive()
                    ?ProbeFailure.transientFailure("egl_thread:join_timeout",null):null;
        }
        <T>T call(Callable<T> action)throws EglUnavailable,ProbeFailure,TupleRejected{
            FutureTask<T> task=new FutureTask<>(action);
            if(!handler.post(task))
                throw ProbeFailure.transientFailure("egl_thread:stopped",null);
            try{return task.get(EGL_TIMEOUT_MILLIS,TimeUnit.MILLISECONDS);}
            catch(TimeoutException error){
                task.cancel(true);
                throw ProbeFailure.transientFailure("egl_thread:timeout",error);
            }catch(InterruptedException error){
                task.cancel(true);Thread.currentThread().interrupt();
                throw ProbeFailure.transientFailure("egl_thread:interrupted",error);
            }catch(java.util.concurrent.CancellationException error){
                throw ProbeFailure.transientFailure("egl_thread:cancelled",error);
            }catch(java.util.concurrent.ExecutionException error){
                Throwable cause=error.getCause();
                if(cause instanceof EglUnavailable value)throw value;
                if(cause instanceof TupleRejected value)throw value;
                if(cause instanceof ProbeFailure value)throw value;
                if(cause instanceof OutOfMemoryError value)
                    throw ProbeFailure.transientFailure("egl_thread:out_of_memory",value);
                throw ProbeFailure.global("egl_thread:"+
                        cause.getClass().getSimpleName(),cause);
            }
        }
        static void clearGlErrors(){
            while(GLES20.glGetError()!=GLES20.GL_NO_ERROR){}
        }
    }
    private static EncoderSelection encoder(VideoMode mode)throws TupleRejected{
        int w=mode.resolution().actual().width(),h=mode.resolution().actual().height(),fps=mode.framesPerSecond();
        for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()){
            if(!info.isEncoder())continue;MediaCodecInfo.CodecCapabilities caps;try{caps=info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);}catch(IllegalArgumentException e){continue;}
            MediaCodecInfo.VideoCapabilities video=caps.getVideoCapabilities();if(video==null)continue;try{if(!video.areSizeAndRateSupported(w,h,fps))continue;}catch(IllegalArgumentException e){continue;}
            Range<Integer> bitrates=video.getBitrateRange();long target=Math.max(1L,(long)w*h*fps/4);return new EncoderSelection(info.getName(),(int)Math.max(bitrates.getLower(),Math.min((long)bitrates.getUpper(),target)));
        }throw new TupleRejected("encoder:no_matching_h264");
    }
    private static final class TupleResources {int cameraTextureId;SurfaceTexture cameraTexture,previewTexture;Surface cameraSurface,previewSurface,encoderSurface;MediaCodec encoder;ImageReader image;EGLSurface previewEgl,encoderEgl;List<OutputConfiguration> outputs;List<OutputConfiguration> outputs(){return outputs;}}
    private record EncoderSelection(String name,int bitrate){}
    private static final class EglUnavailable extends Exception{EglUnavailable(String m){super(m);}}
    private static final class TupleRejected extends Exception{TupleRejected(String m){super(m);}}
    private static final class ProbeFailure extends Exception{
        final Completion completion;final String detail;ProbeFailure(Completion c,String d,Throwable cause){super(d,cause);completion=c;detail=d;}
        static ProbeFailure transientFailure(String d,Throwable c){return new ProbeFailure(Completion.INCOMPLETE_TRANSIENT,d,c);}static ProbeFailure global(String d,Throwable c){return new ProbeFailure(Completion.INCOMPLETE_GLOBAL,d,c);}static ProbeFailure blocked(String d,Throwable c){return new ProbeFailure(Completion.BLOCKED_EXTERNAL,d,c);}Completion completion(){return completion;}String detail(){return detail;}}
    private static String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" required");return value;}
}