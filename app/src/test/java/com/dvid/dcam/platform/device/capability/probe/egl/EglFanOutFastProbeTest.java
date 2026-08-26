package com.dvid.dcam.platform.device.capability.probe.egl;

import static org.junit.jupiter.api.Assertions.*;
import com.dvid.dcam.feature.device.domain.camera.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class EglFanOutFastProbeTest {
    static final CameraId CAMERA=new CameraId("0");
    static final StandardResolution SD=new StandardResolution(StandardResolutionLabel.SD,new CameraResolution(640,480));
    static final StandardResolution HD=new StandardResolution(StandardResolutionLabel.HD,new CameraResolution(1280,720));
    static final StandardResolution FHD=new StandardResolution(StandardResolutionLabel.FHD,new CameraResolution(1920,1080));
    static final VideoMode V30=new VideoMode(HD,30),V60=new VideoMode(HD,60);
    static final ImageMode I1=new ImageMode(SD),I2=new ImageMode(FHD);

    @Test void fullCartesianKeepsOnlySupportedRawTuples(){
        CaptureModeTuple pass=new CaptureModeTuple(V30,I1);
        EglFanOutFastProbe.Result result=run(List.of(V30,V60),List.of(I1,I2),(tuple,a,c)->
                tuple.equals(pass)?EglFanOutFastProbe.Decision.supported("ok"):EglFanOutFastProbe.Decision.rejected("no"));
        assertTrue(result.complete());assertEquals(PipelineAvailability.AVAILABLE,result.evidence().availability());
        assertEquals(Set.of(CandidateKey.forTuple(CAMERA,VideoCodec.H264,EglFanOutFastProbe.PIPELINE_ID,pass)),result.evidence().rawFastCandidates());
        assertTrue(result.evidence().candidateEvidence().isEmpty());
        String summary=EglFanOutFastProbe.completeLog(result,2);
        assertTrue(summary.contains("cameraId=0")&&summary.contains("pipeline=b-camera2-egl-fanout-v1")
                &&summary.contains("codec=h264")&&summary.contains("vfProfileCount=1")
                &&summary.contains("imageProfileCount=2")&&summary.contains("tupleProfileCount=1"));
    }
    @Test void destructiveFalseGetsOneConfirmation(){
        Map<CaptureModeTuple,Integer> counts=new HashMap<>();CaptureModeTuple pass=new CaptureModeTuple(V60,I1);
        EglFanOutFastProbe.Result result=run(List.of(V30,V60),List.of(I1,I2),(tuple,a,c)->{counts.merge(tuple,1,Integer::sum);return tuple.equals(pass)?EglFanOutFastProbe.Decision.supported("ok"):EglFanOutFastProbe.Decision.rejected("no");});
        assertEquals(2,counts.get(new CaptureModeTuple(V30,I1)));assertEquals(2,counts.get(new CaptureModeTuple(V30,I2)));assertEquals(1,counts.get(new CaptureModeTuple(V60,I2)));
        assertTrue(result.attempts().stream().filter(x->x.attempt()==2).allMatch(x->x.confirmation().equals("fps_row")));
    }
    @Test void globalFailureLeavesEarlierFactsUnknown(){
        CaptureModeTuple pass=new CaptureModeTuple(V30,I1);
        EglFanOutFastProbe.Result result=run(List.of(V30),List.of(I1,I2),(tuple,a,c)->tuple.equals(pass)?EglFanOutFastProbe.Decision.supported("ok"):EglFanOutFastProbe.Decision.unbuilt(EglFanOutFastProbe.AttemptResult.INCOMPLETE_GLOBAL,"egl_lost"));
        assertEquals(EglFanOutFastProbe.Completion.INCOMPLETE_GLOBAL,result.completion());assertEquals(PipelineAvailability.UNKNOWN,result.evidence().availability());assertEquals(1,result.evidence().rawFastCandidates().size());
    }
    @Test void pipelineBFailureDoesNotMutateA(){
        VerificationPipelineId a=new VerificationPipelineId("a-camera2-native-surface-sharing-v1");CaptureModeTuple tuple=new CaptureModeTuple(V30,I1);CandidateKey key=CandidateKey.forTuple(CAMERA,VideoCodec.H264,a,tuple);PipelineEvidence evidence=new PipelineEvidence(CAMERA,VideoCodec.H264,a,PipelineAvailability.AVAILABLE,List.of(key),List.of());
        EglFanOutFastProbe.Result b=run(List.of(V30),List.of(I1),(t,x,c)->EglFanOutFastProbe.Decision.rejected("b_fail"));
        assertTrue(b.evidence().rawFastCandidates().isEmpty());assertEquals(Set.of(key),evidence.rawFastCandidates());assertTrue(evidence.effectiveCandidates().contains(key));
    }
    @Test void uncheckedQueryBecomesIncompleteGlobal() {
        EglFanOutFastProbe.Result result=run(List.of(V30),List.of(I1),
                (tuple,attempt,confirmation)->{throw new IllegalStateException("boom");});
        assertEquals(EglFanOutFastProbe.Completion.INCOMPLETE_GLOBAL,result.completion());
        assertEquals(PipelineAvailability.UNKNOWN,result.evidence().availability());
        assertEquals("query:IllegalStateException",result.detail());
    }
    @Test void temporaryAllocationErrorsStayTransient() {
        assertTrue(EglFanOutFastProbe.isTemporaryAllocationFailure(
                android.opengl.EGL14.EGL_BAD_ALLOC));
        assertTrue(EglFanOutFastProbe.isTemporaryAllocationFailure(
                android.opengl.GLES20.GL_OUT_OF_MEMORY));
        assertFalse(EglFanOutFastProbe.isTemporaryAllocationFailure(
                android.opengl.EGL14.EGL_BAD_MATCH));
    }
    @Test void openGuardTransfersOwnershipAcrossTimeoutRace() {
        Object earlyCamera=new Object();
        EglFanOutFastProbe.OpenGuard<Object> callbackFirst=
                new EglFanOutFastProbe.OpenGuard<>();
        assertTrue(callbackFirst.accept(earlyCamera));
        assertSame(earlyCamera,callbackFirst.abandon());
        EglFanOutFastProbe.OpenGuard<Object> timeoutFirst=
                new EglFanOutFastProbe.OpenGuard<>();
        assertNull(timeoutFirst.abandon());
        assertFalse(timeoutFirst.accept(new Object()));
    }
    static EglFanOutFastProbe.Result run(List<VideoMode> v,List<ImageMode> i,EglFanOutFastProbe.TupleQuery q){return EglFanOutFastProbe.runMatrix(CAMERA,v,i,q,System.nanoTime());}
}
