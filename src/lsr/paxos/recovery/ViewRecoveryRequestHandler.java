package lsr.paxos.recovery;

import static lsr.common.ProcessDescriptor.processDescriptor;

import java.util.BitSet;

import lsr.paxos.core.Paxos;
import lsr.paxos.core.Proposer;
import lsr.paxos.core.Proposer.ProposerState;
import lsr.paxos.messages.Message;
import lsr.paxos.messages.Recovery;
import lsr.paxos.messages.RecoveryAnswer;
import lsr.paxos.network.MessageHandler;
import lsr.paxos.storage.Storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewRecoveryRequestHandler implements MessageHandler {
    private final Paxos paxos;

    public ViewRecoveryRequestHandler(Paxos paxos) {
        this.paxos = paxos;
    }

    public void onMessageReceived(Message msg, final int sender) {
        final Recovery recovery = (Recovery) msg;

        paxos.getDispatcher().submit(new Runnable() {
            public void run() {
                Storage storage = paxos.getStorage();
                Proposer proposer = paxos.getProposer();

                if (paxos.getLeaderId() == sender ||
                    recovery.getView() >= storage.getView()) {
                    /*
                     * if current leader is recovering, we cannot respond and we
                     * should change a leader
                     */

                    // OR

                    /*
                     * The recovering process notified me that it crashed in
                     * view recovery.getView()
                     * 
                     * This view is not less then current. View change must be
                     * performed.
                     */

                    logger.info(processDescriptor.logMark_Benchmark,
                            "Delaying receive {} (view change forced)", recovery);

                    if (proposer.getState() != ProposerState.INACTIVE)
                        proposer.stopProposer();
                    proposer.prepareNextView();

                    // reschedule receiving msg
                    onMessageReceived(recovery, sender);
                    return;
                }

                if (paxos.isLeader() &&
                    proposer.getState() == ProposerState.PREPARING) {

                    logger.info(processDescriptor.logMark_Benchmark,
                            "Delaying receive {} (view change forced)", recovery);

                    // wait until we prepare the view
                    proposer.executeOnPrepared(new Proposer.Task() {

                        public void onPrepared() {
                            onMessageReceived(recovery, sender);
                        }

                        public void onFailedToPrepare() {
                            onMessageReceived(recovery, sender);
                        }
                    });
                    return;
                }

                logger.info(processDescriptor.logMark_Benchmark, "Received {}", recovery);

                RecoveryAnswer answer = new RecoveryAnswer(storage.getView(),
                        storage.getLog().getNextId());
                paxos.getNetwork().sendMessage(answer, sender);
            }
        });
    }

    public void onMessageSent(Message message, BitSet destinations) {
    }

    private static final Logger logger = LoggerFactory.getLogger(ViewRecoveryRequestHandler.class);
}