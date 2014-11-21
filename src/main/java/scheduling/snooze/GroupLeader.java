package scheduling.snooze;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.snooze.msg.*;
import simulation.SimulatorManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;


/**
 * Created by sudholt on 25/05/2014.
 */
public class GroupLeader extends Process {
    Host host; //@ Make private
    Hashtable<String, GMInfo> gmInfo = new Hashtable<String, GMInfo>(); //@ Make private
    private String inbox;
    private boolean thisGLToBeTerminated = false;
    private ThreadPool lcAssPool;
    private ThreadPool newGMPool;

    public static enum AssignmentAlg { BESTFIT, ROUNDROBIN };
    private int roundRobin = 0;

    public GroupLeader(Host host, String name) {
        super(host, name);
        this.host = host;
        this.inbox = AUX.glInbox(host.getName());
    }

    @Override
    public void main(String[] strings) {
        lcAssPool = new ThreadPool(this, RunLCAss.class.getName(), AUX.lcPoolSize);
        newGMPool = new ThreadPool(this, RunNewGM.class.getName(), AUX.gmPoolSize);
        Logger.debug("noLCWorker: " + AUX.lcPoolSize + ", noGMWorker: " + AUX.gmPoolSize);

        int n = 1;

        Test.gl = this;
//        Logger.debug("[GL.main] GL started: " + host.getName());
        procSendMyBeats();
//        procNewGM();
        procGMInfo();
        while (!SimulatorManager.isEndOfInjection()) {
            try {
                if (!thisGLToBeTerminated) {
                    SnoozeMsg m = (SnoozeMsg) Task.receive(inbox, AUX.ReceiveTimeout);
                    handle(m);
                    gmDead();
                } else {
                    Logger.err("[GL.main] TBTerminated: " + host.getName());
                    break;
                }
                if(SnoozeProperties.shouldISleep())
                    sleep(AUX.DefaultComputeInterval);
            } catch (HostFailureException e) {
                thisGLToBeTerminated = true;
                break;
            } catch (Exception e) {
                String cause = e.getClass().getName();
                if (cause.equals("org.simgrid.msg.TimeoutException")) {
                    if (n % 10 == 0)
                        Logger.err("[GL.main] PROBLEM? 10 Timeout exceptions: " + host.getName() + ": " + cause);
                    n++;
                } else {
                    Logger.err("[GL.main] PROBLEM? Exception: " + host.getName() + ": " + cause);
                    e.printStackTrace();
                }
                gmDead();
            }
        }
        thisGLToBeTerminated=true;
    }

    void handle(SnoozeMsg m) {
//        Logger.debug("[GL.handle] GLIn: " + m);
        String cs = m.getClass().getSimpleName();
        switch (cs) {
            case "TermGMMsg": handleTermGM(m); break;
            case "TermGLMsg": handleTermGL(m); break;
            case "SnoozeMsg":
                Logger.err("[GL(SnoozeMsg)] Unknown message" + m);
                break;

            case "TestFailGLMsg":
                Logger.err("[GL.main] thisGLToBeTerminated: " + host.getName());
                thisGLToBeTerminated = true; break;
        }
    }

    void handleTermGL(SnoozeMsg m) {
        Logger.debug("[GL(TermGL)] GL to be terminated: " + host.getName());
        thisGLToBeTerminated = true;
    }

    void handleTermGM(SnoozeMsg m) {
        String gm = (String) m.getMessage();
        gmInfo.remove(gm);
        Logger.debug("[GL(TermGM)] GM removed: " + gm);
    }

    void gmCharge(SnoozeMsg m) {
        try {
            String gm = m.getOrigin();
            if (!gmInfo.containsKey(m.getOrigin())) return;
            GMInfo gi = gmInfo.get(gm);
            GMSumMsg.GMSum s = (GMSumMsg.GMSum) m.getMessage();
            GMSum sum = new GMSum(s.getProcCharge(), s.getMemUsed(), s.getNoLCs(), Msg.getClock());
            Logger.info("[GL.gmCharge)] " + gm + ": " + sum + ", " + m);
            gmInfo.put(gm, new GMInfo(Msg.getClock(), sum));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void gmDead() {
        ArrayList<String> deadGMs = new ArrayList<String>();
        if (gmInfo.isEmpty()) return;
        for (String gm: gmInfo.keySet()) {
            GMInfo gi = gmInfo.get(gm);
            if (gi != null) {
                if (AUX.timeDiff(gi.timestamp) > AUX.HeartbeatTimeout) deadGMs.add(gm);
            }
        }
        for (String gm: deadGMs) {
            Logger.imp("[GL.gmDead] GM dead, removed: " + gm + ": " + gmInfo.get(gm).timestamp);
            gmInfo.remove(gm);
        }
    }

    HashMap<String, Integer> assignedLCs = new HashMap<String, Integer>();
    /**
     * Beats to multicast group
     */
    String lcAssignment(String lc) {
        if (gmInfo.size()==0) return "";
        String gm = "";
        switch (AUX.assignmentAlg) {
            case BESTFIT:
                double minCharge = Double.MAX_VALUE, curCharge;
                int noLCs, prevNoLCs = Integer.MAX_VALUE;
                GMSum cs;
                for (String s : gmInfo.keySet()) {
                    cs = gmInfo.get(s).summary;
                    curCharge = cs.procCharge;
                    if (!assignedLCs.containsKey(s)) assignedLCs.put(s, Integer.valueOf(gmInfo.get(s).summary.noLCs));
                    noLCs = assignedLCs.get(s);
                    Logger.debug("[GL.lcAssignment(BESTFIT)] GM: " + s + ", min/charge: " + minCharge+"/"+curCharge
                            + ", min/noLCs/assLCs: " + prevNoLCs+"/"+noLCs+"/"+assignedLCs.get(s));
                    if (minCharge > curCharge) {
                        minCharge = curCharge;
                        prevNoLCs = noLCs;
                        gm = s;
                    }
                    if (minCharge == curCharge) {
                        if (noLCs < prevNoLCs) { gm = s; prevNoLCs = noLCs; }
                    }
                }
                assignedLCs.put(gm, Integer.valueOf(assignedLCs.get(gm).intValue() + 1));
                Logger.debug("[GL.lcAssignment(BESTFIT)] GM selected: " + gm + ", #GMs: " + gmInfo.size());
                break;
            case ROUNDROBIN:
                roundRobin = roundRobin % gmInfo.size(); // GMs may have died in the meantime
                ArrayList<String> gms = new ArrayList<>(gmInfo.keySet());
                gm = gms.get(roundRobin);
                roundRobin++;
                Logger.debug("[GL.lcAssignment(ROUNDROBIN)] GM selected: " + gm + ", #GMs: " + gmInfo.size());
                break;
        }
        return gm;
    }

    void procGMInfo() {
        try {
            new Process(host, host.getName() + "-gmPeriodic") {
                public void main(String[] args) throws HostFailureException {
                    while (!thisGLToBeTerminated) {
                        try {
                            SnoozeMsg m = (SnoozeMsg)
                                    Task.receive(inbox + "-gmPeriodic", AUX.HeartbeatTimeout);
                            Logger.info("[GL.procGMInfo] " + m);

                            if (m instanceof GMSumMsg)   gmCharge(m);
                            else {
                                Logger.err("[GL.procGMInfo] Unknown message: " + m);
                                continue;
                            }
                            if(SnoozeProperties.shouldISleep())
                                sleep(AUX.DefaultComputeInterval);
                        }
                        catch (TimeoutException e) {
                            Logger.exc("[GL.procGMInfo] PROBLEM? Timeout Exception");
                        } catch (HostFailureException e) {
                            thisGLToBeTerminated = true;
                            break;
                        } catch (Exception e) {
                            Logger.exc("[GL.procGMInfo] Exception, " + host.getName() + ": " + e.getClass().getName());
                            e.printStackTrace();
                        }
                    }
                }
            }.start();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends beats to multicast group
     */
    void procSendMyBeats() {
        try {
            new Process(host, host.getName() + "-glBeats") {
                public void main(String[] args) {
                    String glHostname = host.getName();
                    while (!thisGLToBeTerminated) {
                        try {
                            BeatGLMsg m =
                                    new BeatGLMsg(Msg.getClock(), AUX.multicast+"-relayGLBeats", glHostname, null);
                            m.send();
                            Logger.info("[GL.procSendMyBeats] " + m);
                            sleep(AUX.HeartbeatInterval*1000);
                        } catch (HostFailureException e) {
                            thisGLToBeTerminated = true;
                            break;
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
            }.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public class RunNewGM implements Runnable {
        public RunNewGM() {};

        public void run() {
            try {
                NewGMMsg m = (NewGMMsg)
                        Task.receive(inbox + "-newGM", AUX.PoolingTimeout);
                String gmHostname = ((GroupManager) m.getMessage()).host.getName();
                if (gmInfo.containsKey(gmHostname))
                    Logger.err("[GL.RunNewGM] GM " + gmHostname + " exists already");
                // Add GM
                GMInfo gi = new GMInfo(Msg.getClock(), new GMSum(0, 0, 0, Msg.getClock()));
                gmInfo.put(gmHostname, gi);
                // Acknowledge integration
                Logger.imp("[GL.RunNewGM] GM added: " + gmHostname + ", " + m);
            } catch (HostFailureException e) {
                thisGLToBeTerminated = true;
            } catch (Exception e) {
                Logger.exc("[GL.RunNewGM] Exception, " + host.getName() + ": " + e.getClass().getName());
                e.printStackTrace();
            }
        }
    }

    public class RunLCAss implements Runnable {
        public RunLCAss() {};

        public void run() {
            LCAssMsg m = null;
            try {
                Logger.info("[GL.RunLCAss] Wait for tasks: " + GroupLeader.this.inbox + "-lcAssign");
                m = (LCAssMsg) Task.receive(inbox + "-lcAssign", AUX.PoolingTimeout);
                Logger.info("[GL.RunLCAss] Task received: " + m);
                String gm = lcAssignment((String) m.getMessage());
                m = new LCAssMsg(gm, m.getReplyBox(), host.getName(), null);
                m.send();
                Logger.imp("[GL.RunLCAss] GM assigned: " + m);
            } catch (TimeoutException e) {
                Logger.exc("[GL.RunLCAss] PROBLEM? Timeout Exception");
            } catch (HostFailureException e) {
                Logger.err("[GL.RunLCAss] HostFailure Exception should never happen!: " + host.getName());
            } catch (Exception e) {
                Logger.exc("[GL.RunLCAss] Exception");
            }
        }
    }

    void dispatchVMRequest() {

    }

    void assignLCToGM() {

    }

    public class GMInfo {
        double timestamp;
        GMSum  summary;

        GMInfo(double ts, GMSum s) {
            this.timestamp = ts; summary = s;
        }
    }

    /**
     * GM charge summary info
     */
    public class GMSum {
        double procCharge;
        int    memUsed;
        int    noLCs;
        double   timestamp;

        GMSum(double p, int m, int noLCs, double ts) {
            this.procCharge = p; this.memUsed = m; this.noLCs = noLCs; this.timestamp = ts;
        }
    }
}
