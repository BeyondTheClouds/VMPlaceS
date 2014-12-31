package scheduling.snooze;

import configuration.SimulatorProperties;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.snooze.msg.SnoozeMsg;
import scheduling.snooze.msg.TestFailGLMsg;
import scheduling.snooze.msg.TestFailGMMsg;
import simulation.SimulatorManager;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by sudholt on 20/07/2014.
 */
public class Test extends Process {
    static String name;
    static Host host;
    static String inbox;
    public boolean testsToBeTerminated = false;

    static Multicast multicast;
    static GroupLeader gl;
    static ConcurrentHashMap<String, GroupManager> gmsCreated = new ConcurrentHashMap<>();
    static ConcurrentHashMap<String, GroupManager> gmsJoined = new ConcurrentHashMap<>();
    static ConcurrentHashMap<String, LocalController> lcsCreated = new ConcurrentHashMap<>();
    static ConcurrentHashMap<String, LCJoined> lcsJoined = new ConcurrentHashMap<>();

    static int noGMJoins = 0;
    static int noLCJoins = 0;

    static String gm = "";
    static SnoozeMsg m = null;

    public Test(Host host, String name) {
        super(host, name);
        this.host = host;
        this.name = name;
        this.inbox = "test";
    }

    @Override
    public void main(String[] strings) throws MsgException {
        try {
//        procAddLCs();
//        procAddGMs();
//        procFailGLs();
//        procFailGMs();
            while (!testsToBeTerminated && !SimulatorManager.isEndOfInjection()) {
                dispInfo();
                sleep(1000*SnoozeProperties.getInfoPeriodicity());
            }
        } catch (HostFailureException e) {
            testsToBeTerminated = true;
            Logger.err("[Test.main] HostFailureException");
        }
    }

    void procAddGMs() throws HostNotFoundException {
        new Process(host, host.getName() + "-addGMs") {
            public void main(String[] args) throws HostFailureException, HostNotFoundException, NativeException {
                try {
                    int lcNo = SimulatorProperties.getNbOfHostingNodes(); // no. of statically allocated LCs
                    int gmNo = SimulatorProperties.getNbOfServiceNodes(); // no. of statically allocated GMs
//                for (int i = 0; i < SimulatorProperties.getNbOfServiceNodes()/2 && !testsToBeTerminated; i++) {
                    for (int i = 0; i < SimulatorProperties.getNbOfServiceNodes() && !testsToBeTerminated; i++) {
                        sleep(250);
                        String[] gmArgs = new String[]{"node" + (gmNo + lcNo), "dynGroupManager-" + (gmNo + lcNo)};
                        GroupManager gm =
                                new GroupManager(Host.getByName("node" + (gmNo + lcNo)), "dynGroupManager-" + (gmNo + lcNo), gmArgs);
                        gm.start();
                        Logger.debug("[Test.addLCs] Dyn. GM added: " + gmArgs[1]);
                        gmNo++;
                    }
                } catch (HostFailureException e) {
                    testsToBeTerminated = true;
                    Logger.err("[Test.procAddGMs] HostFailureException");
                }
            }
        }.start();
    }

    void procAddLCs() throws HostNotFoundException {
        new Process(host, host.getName() + "-addLCs") {
            public void main(String[] args) throws HostFailureException, HostNotFoundException, NativeException {
                try {
                    sleep(6000);
                    int lcNo = 0; // no. of statically allocated LCs
                    for (int i = 0; i < SimulatorProperties.getNbOfHostingNodes() && !testsToBeTerminated; i++) {
                        String[] lcArgs = new String[]{"node" + lcNo, "dynLocalController-" + lcNo};
                        LocalController lc =
                                new LocalController(Host.getByName("node" + lcNo), "dynLocalController-" + lcNo, lcArgs);
                        lc.start();
                        Logger.info("[Test.addLCs] Dyn. LC added: " + lcArgs[1]);
                        lcNo++;
                        sleep(2000);
                    }
                } catch (HostFailureException e) {
                    testsToBeTerminated = true;
                    Logger.err("[Test.procAddLCs] HostFailureException");
                }
            }
        }.start();
    }

    void procFailGLs() throws HostNotFoundException {
        new Process(host, host.getName() + "-terminateGMs") {
            public void main(String[] args) throws HostFailureException {
                try {
                    sleep(4000);
                    for (int i = 0; i < SimulatorProperties.getNbOfServiceNodes() / 2 && !testsToBeTerminated; i++) {
                        if (multicast.gmInfo.size() < 3) {
                            Logger.debug("[Test.failGLs] #GMs: " + multicast.gmInfo.size());
                            sleep(3000);
                            continue;
                        }
                        m = new TestFailGLMsg(name, AUX.glInbox(multicast.glHostname), null, null);
                        m.send();
                        Logger.imp("[Test.failGLs] GL failure: " + Test.gl.getHost().getName());
                        sleep(1537);
//                    break;
                    }
                } catch (HostFailureException e) {
                    testsToBeTerminated = true;
                    Logger.err("[Test.procFailGLs] HostFailureException");
                }
            }
        }.start();
    }

    void procFailGMs() throws HostNotFoundException {
        new Process(host, host.getName() + "-terminateGMs") {
            public void main(String[] args) throws HostFailureException {
                try {
                    sleep(5000);
                    for (int i = 0; i < SimulatorProperties.getNbOfServiceNodes() / 2 && !testsToBeTerminated; i++) {
                        if (multicast.gmInfo.size() < 3) {
                            Logger.info("[Test.failGMs] #GMs: " + multicast.gmInfo.size());
                            sleep(1777);
                            continue;
                        }
                        gm = new ArrayList<String>(multicast.gmInfo.keySet()).get(0);
                        m = new TestFailGMMsg(name, AUX.gmInbox(gm), null, null);
                        m.send();
                        Logger.imp("[Test.failGMs] Term. GM: " + gm + ", #GMs: " + multicast.gmInfo.size());
                        sleep(1777);
//                    break;
                    }
                } catch (HostFailureException e) {
                    testsToBeTerminated = true;
                    Logger.err("[Test.procFailGMs] HostFailureException");
                }
            }
        }.start();
    }

    void dispInfo() {
        int i = 0, al = 0, gmal = 0;
        Logger.imp("\n\n[Test.dispInfo] MUL.GL: " + multicast.glHostname +
                ", #MUL.gmInfo: " + multicast.gmInfo.size() +
                ", #MUL.lcInfo: " + multicast.lcInfo.size() + ", #Test.gmsCreated " + Test.gmsCreated.size());
        Logger.imp("    ----");
        for (String gm : multicast.gmInfo.keySet()) {
            int mulLCs = 0, testLCsCreated = 0, testLCsJoined = 0;
            for (String lc : multicast.lcInfo.keySet()) {
                if (multicast.lcInfo.get(lc).gmHost.equals(gm)) {
                    mulLCs++;
                    if (Test.lcsCreated.containsKey(lc)) testLCsCreated++;
                    if (Test.lcsJoined.containsKey(lc)) testLCsJoined += Test.getNoGMsJoinedLC(lc);
                }
            }
            String gmLeader = "";
            int gml = 0;
            for (String gmn : Test.gmsCreated.keySet()) {
                GroupManager gmo = Test.gmsCreated.get(gmn);
                if (gmn.equals(gm)) {
                    gmLeader = gmo.glHostname;
                    gml = gmo.lcInfo.size();
                    gmal += gml;
                }
            }
            Logger.imp("    MUL.GM: " + gm + ", MUL.GM.#LCs: " + mulLCs
                    + ", Test.GM.#LCs join/create: " + testLCsJoined + "/" + testLCsCreated
                    + ", Test.GMLeader: " + gmLeader);
//            Logger.imp("                         GM.#LCs: " + gml + ", Test.GMLeader: " + gmLeader);
            i++;
            al += mulLCs;
        }
        Logger.imp("    ----");
        if (gl != null)
            Logger.imp("    Test.GL: " + gl.host.getName()
                    + ", Test.GL.#GM: " + gl.gmInfo.size() + ", MUL.GM.#LCs: " + al + ", Test.GM.#LCs: " + gmal);
        Logger.imp("    No. GM joins: " + noGMJoins + ", No. LC joins: " + noLCJoins + "\n");
    }

    static class LCJoined {
        LocalController lco;
        ArrayList<String> gms;
    }

    static void removeJoinedLC(String lc, String gm, String m) {
        if (lcsJoined.containsKey(lc)) {
            LCJoined tlj = lcsJoined.get(lc);
            if (tlj.gms.contains(gm)) {
                tlj.gms.remove(gm);
                Logger.debug(m + " removeJoinedLC: LC: " + lc + ", GM: " + gm);
                if (tlj.gms.size() == 0) {
                    lcsJoined.remove(lc);
                    Logger.debug(m + ", removeJoinedLC: Last GM removed LC: " + lc + ", GM: " + gm);
                }
            }
        }
        else Logger.debug(m + ", removeJoinedLC: No LC: " + lc + ", GM: " + gm);
    }

    static void putJoinedLC(String lc, LocalController lco, String gm, String m) {
        if (lcsJoined.containsKey(lc)) {
            LCJoined tlj = lcsJoined.get(lc);
            if (!tlj.gms.contains(gm)) {
                tlj.gms.add(gm);
                Logger.debug(m + " putJoinedLC: GM added LC: " + lc + ", GM: " + gm + ", LCO: " + lco);
            } else Logger.err(m + " putJoinedLC: Double LC: " + lc + ", GM: " + gm + ", LCO: " + lco);
        } else {
            LCJoined tlj = new LCJoined();
            tlj.lco = lco;
            ArrayList<String> al = new ArrayList<>();
            al.add(gm);
            tlj.gms = al;
            lcsJoined.put(lc, tlj);
            Logger.debug(m + " putJoinedLC: New LC: " + lc + ", GM: " + gm + ", LCO: " + lco);
        }
    }

    static int getNoGMsJoinedLC(String lc) {
        if (!lcsJoined.containsKey(lc)) return 0;
        else return lcsJoined.get(lc).gms.size();
    }
}
