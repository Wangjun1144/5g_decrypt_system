package com.example.scene.decodersystem;

import com.example.procedure.processing.context.UeContextService;
import com.example.procedure.model.decrypt.DecryptAttemptResult;
import com.example.procedure.model.MessageCategory;
import com.example.procedure.model.MessageProcessingResult;
import com.example.procedure.model.SignalingMessage;
import com.example.procedure.model.UEContext;
import com.example.procedure.processing.message.MessageDecryptFlow;
import com.example.procedure.processing.message.MessageProcessingCoordinator;
import com.example.procedure.processing.message.MessageProcessingReporter;
import com.example.procedure.processing.message.MessageRetryTrigger;
import com.example.procedure.processing.message.classify.MessageClassificationOutcome;
import com.example.procedure.processing.message.classify.MessageClassificationService;
import com.example.procedure.processing.message.decrypt.MessageDecryptStage;
import com.example.procedure.processing.message.event.MessageStageEventPublisher;
import com.example.procedure.processing.message.result.MessageProcessingResultAssembler;
import com.example.procedure.processing.message.retry.PendingDecryptRetryService;
import com.example.procedure.processing.message.runtime.MessageProcessingContext;
import com.example.procedure.processing.message.runtime.MessageProcessingRequest;
import com.example.procedure.processing.pending.event.PendingDecryptEventPublisher;
import com.example.procedure.processing.pending.queue.PendingDecryptQueue;
import com.example.procedure.processing.dispatch.ProcedureDispatchOutcome;
import com.example.procedure.processing.procedure.recognize.ProcedureRecognitionOutcome;
import com.example.procedure.processing.procedure.stage.ProcedureStageOutcome;
import com.example.procedure.processing.procedure.stage.ProcedureProcessingStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MessageProcessingCoordinator 閻ㄥ嫭娓剁亸蹇庡瘜闁炬崘顢戞稉鐑樼ゴ鐠囨洩鎷?
 *
 * 鏉╂瑧绮嶅ù瀣槸妤犲矁鐦夐惃鍕Ц閳ユ粈瀵岄柧鐐付閸掓儼顕㈡稊澶嗏偓婵撶礉娑撳秵妲搁崗铚傜秼妤犲矁鐦夋稉姘鐟欏嫬鍨紒鍡氬Ν閿?
 *
 * 瑜版挸澧犻柌宥囧仯妤犲矁鐦夐敓?
 * 1. 鐟欙絽鐦?WAITING 閺冭绱?
 *    - 鏉╂稑鍙?pending 闂冪喎鍨?
 *    - 娑撳秷绻橀崗銉︾ウ缁嬪妯侀敓?
 *    - 娑撳秷袝閿?pending 闁插秷鐦?
 *
 * 2. 鐟欙絽鐦?OK 娑撴柨娲栧ù浣瑰灇閸旂喐妞傞敓?
 *    - 娴兼岸鍣搁弬鎷岀箻閸忋儱鐣弫缈犲瘜閿?
 *    - 缁楊兛绨╂潪顔炬埛缂侇叀绻橀崗銉︾ウ缁嬪妯佸▓闈涜嫙鐟欙箑褰?pending 闁插秷鐦?
 *
 * 3. 娑撳秹娓剁憰浣瑰絹閸撳秶绮ㄩ弶鐔告閿?
 *    - 娴兼俺绻橀崗銉︾ウ缁嬪妯侀敓?
 *    - 娴兼俺袝閿?pending 闁插秷鐦?
 *
 * 濞夈劍鍓伴敓?
 * MessageProcessingCoordinator 瑜版挸澧犳稉宥呭涧閺勵垯绶烽敓?
 * messageDecryptStage.handleEncryptedMessageIfNeeded(...) 閻ㄥ嫯绻戦崶鐐测偓纭风礉
 * 鏉╂ü绶烽敓?context.isDecryptOk() / context.isDecryptWaiting()閿?
 *
 * 閸ョ姵顒濆ù瀣槸閿?mock 鐟欙絽鐦戦梼鑸殿唽閺冭绱濊箛鍛淬€忛崥灞炬閿?decryptResult 閸愭瑥娲?context閿?
 */
class MessageProcessingCoordinatorUnitTests {

    @Mock
    private UeContextService ueContextService;

    @Mock
    private MessageClassificationService classificationService;

    @Mock
    private MessageDecryptStage messageDecryptStage;

    @Mock
    private ProcedureProcessingStage procedureProcessingStage;

    @Mock
    private PendingDecryptQueue pendingDecryptQueue;

    @Mock
    private PendingDecryptRetryService pendingDecryptRetryService;

    @Mock
    private MessageStageEventPublisher stageEventPublisher;

    @Mock
    private PendingDecryptEventPublisher pendingDecryptEventPublisher;

    private MessageProcessingReporter reporter;
    private MessageDecryptFlow messageDecryptFlow;
    private MessageRetryTrigger messageRetryTrigger;
    private MessageProcessingCoordinator messageProcessingCoordinator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        MessageProcessingResultAssembler resultAssembler = new MessageProcessingResultAssembler();
        reporter = new MessageProcessingReporter(
                stageEventPublisher,
                pendingDecryptEventPublisher,
                pendingDecryptQueue,
                resultAssembler
        );
        messageDecryptFlow = new MessageDecryptFlow(
                messageDecryptStage,
                pendingDecryptQueue,
                resultAssembler,
                reporter
        );
        messageRetryTrigger = new MessageRetryTrigger(
                ueContextService,
                pendingDecryptRetryService,
                reporter
        );

        messageProcessingCoordinator = new MessageProcessingCoordinator(
                ueContextService,
                classificationService,
                procedureProcessingStage,
                messageDecryptFlow,
                messageRetryTrigger,
                resultAssembler,
                reporter
        );
    }

    /**
     * 閺嬪嫰鈧姳绔撮弶鈩冩付鐏忓繐褰查悽銊︾Х閹垽鎷?
     *
     * 瑜版挸澧犲ù瀣槸閸欘亜鍙у▔銊ゅ瘜闁惧墽绱幒鎺炵礉娑撳秴鍙у▔銊ュ徔娴ｆ挸宕楃拋顔肩摟濞堥潧鐣弫瀛樷偓褝鎷?
     */
    private SignalingMessage buildMessage() {
        SignalingMessage msg = new SignalingMessage();
        msg.setUeId("460011234567890");
        msg.setMsgType("Initial UE Message");
        msg.setIface("N2");
        msg.setDirection("UL");
        msg.setProtocolLayer("NGAP");
        msg.setTimestamp(System.currentTimeMillis());
        msg.setFrameNo(1L);
        return msg;
    }

    /**
     * 鐠佲晛鍨庣猾濠氭▉濞堝灚濡稿☉鍫熶紖閺嶅洩顔囬敓?PROCEDURE_DRIVING閿?
     * 鏉╂瑦鐗辫ぐ鎾插瘜闁惧墽鎴风紒顓炵窔閸氬氦铔嬮弮璁圭礉濞翠胶鈻奸梼鑸殿唽鐏忓崬鍙挎径鍥箻閸忋儲娼禒璁规嫹?
     */
    private void stubClassificationAsProcedureDriving() {
        doAnswer(invocation -> {
            MessageProcessingContext context = invocation.getArgument(0);
            context.setCategory(MessageCategory.PROCEDURE_DRIVING);
            return MessageClassificationOutcome.of(MessageCategory.PROCEDURE_DRIVING);
        }).when(classificationService).classify(any(MessageProcessingContext.class));
    }

    /**
     * 濡剝瀚欑憴锝呯槕闂冭埖顔屾潻鏂挎礀 WAITING閿涘苯鑻熼幎濠勭波閺嬫粌鍟撻敓?context閿?
     */
    private void stubDecryptWaiting(DecryptAttemptResult.WaitReason reason) {
        doAnswer(invocation -> {
            MessageProcessingContext context = invocation.getArgument(0);
            DecryptAttemptResult result = DecryptAttemptResult.waiting(reason);
            context.setDecryptResult(result);
            return result;
        }).when(messageDecryptStage).handleEncryptedMessageIfNeeded(any(MessageProcessingContext.class));
    }

    /**
     * 濡剝瀚欑憴锝呯槕闂冭埖顔岀粭顑跨鏉烆喛绻戦敓?OK閵嗕胶顑囨禍宀冪枂鏉╂柨娲?null閿?
     * 楠炶泛婀粭顑跨鏉烆喗濡?OK 閸愭瑥娲?context閿?
     *
     * 鏉╂瑦鐗遍崣顖欎簰閻喎鐤勭憴锕€褰?MessageProcessingCoordinator 閻ㄥ嫧鈧粌娲栧ù浣告倵闁帒缍婇柌宥嗘煀鏉╂稑鍙嗘稉濠氭懠閳ユ繈鈧槒绶敓?
     */
    private void stubDecryptOkThenContinue() {
        doAnswer(invocation -> {
            MessageProcessingContext context = invocation.getArgument(0);
            DecryptAttemptResult result = DecryptAttemptResult.ok();
            context.setDecryptResult(result);
            return result;
        }).doAnswer(invocation -> {
            MessageProcessingContext context = invocation.getArgument(0);
            context.setDecryptResult(null);
            return null;
        }).when(messageDecryptStage).handleEncryptedMessageIfNeeded(any(MessageProcessingContext.class));
    }

    /**
     * 濡剝瀚欒ぐ鎾冲濞戝牊浼呴崷銊ㄐ掔€靛棝妯佸▓鍨￥闂団偓閹绘劕澧犵紒鎾存将娑撳鎽奸敓?
     *
     * 濞夈劍鍓版潻娆撳櫡閸氬本妞傞弰鎯х础閿?decryptResult 濞撳懐鈹栭敓?
     * 闁灝鍘ゆ稉濠佺瑓閺傚洨濮搁幀浣稿閸濆秴鎮楃紒顓炲灲閺傤叏鎷?
     */
    private void stubDecryptNoEarlyExit() {
        doAnswer(invocation -> {
            MessageProcessingContext context = invocation.getArgument(0);
            context.setDecryptResult(null);
            return null;
        }).when(messageDecryptStage).handleEncryptedMessageIfNeeded(any(MessageProcessingContext.class));
    }

    @Test
    @DisplayName("鐟欙絽鐦?WAITING 閺冭泛绨叉潻娑樺弳 pending 闂冪喎鍨敍灞借嫙閹绘劕澧犵紒鎾存将瑜版挸澧犳稉濠氭懠")
    void processShouldEnqueueWhenDecryptWaiting() {
        SignalingMessage msg = buildMessage();
        UEContext ueContext = new UEContext();

        stubClassificationAsProcedureDriving();
        stubDecryptWaiting(DecryptAttemptResult.WaitReason.WAIT_ALG);

        when(ueContextService.getContext(msg.getUeId())).thenReturn(ueContext);

        MessageProcessingResult result = messageProcessingCoordinator.process(
                MessageProcessingRequest.of(msg)
        );

        assertNotNull(result);

        verify(classificationService, times(1))
                .classify(any(MessageProcessingContext.class));

        // 閸欘亙绱扮拠璇插絿娑撯偓濞嗏€茬瑐娑撳鏋冮敍灞芥礈閿?WAITING 閸氬簼绱伴幓鎰鏉╂柨娲栭敓?
        verify(ueContextService, times(1)).getContext(msg.getUeId());

        verify(messageDecryptStage, times(1))
                .handleEncryptedMessageIfNeeded(any(MessageProcessingContext.class));
        verify(messageDecryptStage, never())
                .handleDecryptSuccess(any(MessageProcessingContext.class));

        verify(pendingDecryptQueue, times(1))
                .enqueue(eq(msg.getUeId()), eq(msg), eq(DecryptAttemptResult.WaitReason.WAIT_ALG));

        verifyNoInteractions(procedureProcessingStage);
        verifyNoInteractions(pendingDecryptRetryService);
    }

    @Test
    @DisplayName("鐟欙絽鐦戦幋鎰娑撴柨娲栧ù浣瑰灇閸旂喐妞傞敍灞界安闁插秵鏌婃潻娑樺弳鐎瑰本鏆ｆ稉濠氭懠")
    void processShouldReenterMainChainWhenDecryptSuccessAndReentered() {
        SignalingMessage msg = buildMessage();
        UEContext ueContext = new UEContext();

        stubClassificationAsProcedureDriving();
        stubDecryptOkThenContinue();

        when(ueContextService.getContext(msg.getUeId())).thenReturn(ueContext);
        when(messageDecryptStage.handleDecryptSuccess(any(MessageProcessingContext.class)))
                .thenReturn(true);
        when(procedureProcessingStage.process(any(MessageProcessingContext.class)))
                .thenReturn(ProcedureStageOutcome.of(
                        true,
                        ProcedureRecognitionOutcome.of(null, false, false, false),
                        ProcedureDispatchOutcome.of(true, false, null, null)
                ));

        MessageProcessingResult result = messageProcessingCoordinator.process(
                MessageProcessingRequest.of(msg)
        );

        assertNotNull(result);

        // 缁楊兛绔撮敓?+ 閸ョ偞绁﹂崥搴ｆ畱缁楊兛绨╂潪顕嗙礉閸忓彉琚卞▎鈽呮嫹?
        verify(classificationService, times(2))
                .classify(any(MessageProcessingContext.class));

        verify(messageDecryptStage, times(2))
                .handleEncryptedMessageIfNeeded(any(MessageProcessingContext.class));

        // 鐟欙絽鐦戦幋鎰閸ョ偞绁﹂崝銊ょ稊閸欘亜婀粭顑跨鏉烆喖褰傞悽鐔剁濞嗏槄鎷?
        verify(messageDecryptStage, times(1))
                .handleDecryptSuccess(any(MessageProcessingContext.class));

        // 缁楊兛绨╂潪顔藉娴兼氨鎴风紒顓＄箻閸忋儲绁︾粙瀣▉濞堢鎷?
        verify(procedureProcessingStage, times(1))
                .process(any(MessageProcessingContext.class));

        // 缁楊兛绨╂潪顔剧波閺夌喎鎮楁导姘跺櫢閺傛媽顕伴崣鏍︾瑐娑撳鏋冮獮鎯靶曢敓?pending 闁插秷鐦敓?
        verify(ueContextService, times(3)).getContext(msg.getUeId());
        verify(pendingDecryptRetryService, times(1))
                .retryPendingDecrypt(eq(msg.getUeId()), eq(ueContext), any());

        verifyNoInteractions(pendingDecryptQueue);
    }

    @Test
    @DisplayName("process should continue to procedure stage and retry pending work")
    void processShouldContinueToProcedureStageAndRetryPending() {
        SignalingMessage msg = buildMessage();
        UEContext ueContext = new UEContext();

        stubClassificationAsProcedureDriving();
        stubDecryptNoEarlyExit();
        when(procedureProcessingStage.process(any(MessageProcessingContext.class)))
                .thenReturn(ProcedureStageOutcome.of(
                        true,
                        ProcedureRecognitionOutcome.of(null, false, false, false),
                        ProcedureDispatchOutcome.of(true, false, null, null)
                ));

        when(ueContextService.getContext(msg.getUeId())).thenReturn(ueContext);

        MessageProcessingResult result = messageProcessingCoordinator.process(
                MessageProcessingRequest.of(msg)
        );

        assertNotNull(result);

        verify(classificationService, times(1))
                .classify(any(MessageProcessingContext.class));

        // 娑撯偓濞嗏剝妲告稉濠氭懠瀵偓婵妞傜拠璇插絿娑撳﹣绗呴弬鍥风礉娑撯偓濞嗏剝妲稿ù浣衡柤闂冭埖顔岄崥搴ㄥ櫢閺傛媽顕伴崣鏍ㄦ付閺傞绗傛稉瀣瀮閿?
        verify(ueContextService, times(2)).getContext(msg.getUeId());

        verify(messageDecryptStage, times(1))
                .handleEncryptedMessageIfNeeded(any(MessageProcessingContext.class));
        verify(messageDecryptStage, never())
                .handleDecryptSuccess(any(MessageProcessingContext.class));

        verify(procedureProcessingStage, times(1))
                .process(any(MessageProcessingContext.class));

        verify(pendingDecryptRetryService, times(1))
                .retryPendingDecrypt(eq(msg.getUeId()), eq(ueContext), any());

        verifyNoInteractions(pendingDecryptQueue);
    }
}

