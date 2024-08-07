//package org.integratedmodelling.klab.api.services.runtime.kactors;
//
//import org.integratedmodelling.klab.api.scope.Scope;
//import org.integratedmodelling.klab.api.scope.Scope.Status;
//
//import java.io.Serial;
//import java.io.Serializable;
//import java.util.concurrent.atomic.AtomicLong;
//
///**
// * Superclass for messages to/from actor. The general pattern is that whenever an action has finished with a
// * status, the actor will send the same message that has triggered it after completing it with a status and
// * any other needed information so that the scope can be updated at the receiving end.
// * <p>
// * All messages have a unique ID which serves as task ID when the message is launching a task. Notifications
// * through the scope should always include the task ID.
// *
// * @author Ferd
// */
//public abstract class AgentMessage implements Serializable, VM.AgentMessage {
//
//    @Serial
//    private static final long serialVersionUID = 721530303478254820L;
//    private static AtomicLong nextId = new AtomicLong(0L);
//
//    /**
//     * Send a FINAL response, which will remove the response handler at the calling scope and prevent any
//     * further action from responses to this message.
//     *
//     * @param status
//     * @param data
//     * @return
//     */
//    public AgentResponse response(Status status, Object... data) {
//        AgentResponse ret = new AgentResponse();
//        ret.setStatus(status);
//        ret.setId(id);
//        ret.setRemoveHandler(true);
//        if (data != null) {
//            for (int i = 0; i < data.length; i++) {
//                ret.getData().put(data[i].toString(), data[++i]);
//            }
//        }
//        return ret;
//    }
//
//    /**
//     * Send a NON-FINAL response, leaving the response handler in place to handle other responses until a
//     * final one is received. A final response must be sent in all possible situations.
//     *
//     * @param status
//     * @param data
//     * @return
//     */
//    public AgentResponse statusResponse(Status status, Object... data) {
//        AgentResponse ret = new AgentResponse();
//        ret.setStatus(status);
//        ret.setId(id);
//        ret.setRemoveHandler(false);
//        if (data != null) {
//            for (int i = 0; i < data.length; i++) {
//                ret.getData().put(data[i].toString(), data[++i]);
//            }
//        }
//        return ret;
//    }
//
//    private Status status = Status.STARTED;
//    private long id = nextId.incrementAndGet();
//
//    public Status getStatus() {
//        return status;
//    }
//
//    public void setStatus(Status status) {
//        this.status = status;
//    }
//
//    public long getId() {
//        return id;
//    }
//
//    public void setId(long id) {
//        this.id = id;
//    }
//
//}
