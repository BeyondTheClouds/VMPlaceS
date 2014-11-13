package scheduling.snooze;

import configuration.SimulatorProperties;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.snooze.msg.SnoozeMsg;
import scheduling.snooze.msg.TestFailGLMsg;
import scheduling.snooze.msg.TestFailGMMsg;
import simulation.SimulatorManager;

import java.util.ArrayList;
import java.util.Hashtable;

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
    static Hashtable<String, GroupManager> gms = new Hashtable<>();
    static Hashtable<String, LocalController> lcs = new Hashtable<>();


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
//        procAddLCs();
//        procAddGMs();
//        procFailGLs();
//        procFailGMs();
        while (!testsToBeTerminated && !SimulatorManager.isEndOfInjection()) {
            dispInfo();
//            sleep(1000*SnoozeProperties.getInfoPeriodicity());
            sleep(1000);
        }
    }

    void procAddGMs() throws HostNotFoundException {
        new Process(host, host.getName() + "-addGMs") {
            public void main(String[] args) throws HostFailureException, HostNotFoundException, NativeException {
//                sleep(5000);
                int lcNo = SimulatorProperties.getNbOfHostingNodes();
                int gmNo = 1; // no. of statically allocated LCs
                for (int i = 0; i < SimulatorProperties.getNbOfServiceNodes()/2 && !testsToBeTerminated; i++) {
                    String[] gmArgs = new String[]{"node" + (gmNo+lcNo), "dynGroupManager-" + (gmNo+lcNo)};
                    GroupManager gm =
                            new GroupManager(Host.getByName("node" + (gmNo+lcNo)), "dynGroupManager-" + (gmNo+lcNo), gmArgs);
                    gm.start();
                    Logger.debug("[Test.addLCs] Dyn. GM added: " + gmArgs[1]);
                    gmNo++;
                    sleep(1477);
                }
            }
        }.start();
    }

    void procAddLCs() throws HostNotFoundException {
        new Process(host, host.getName() + "-addLCs") {
            public void main(String[] args) throws HostFailureException, HostNotFoundException, NativeException {
//                sleep(777);
                int lcNo = 0; // no. of statically allocated LCs
                for (int i=0; i< SimulatorProperties.getNbOfHostingNodes() && !testsToBeTerminated; i++) {
                    String[] lcArgs = new String[] {"node"+lcNo, "dynLocalController-"+lcNo};
                    LocalController lc =
                            new LocalController(Host.getByName("node"+lcNo), "dynLocalController-"+lcNo, lcArgs);
                    lc.start();
                    Logger.info("[Test.addLCs] Dyn. LC added: " + lcArgs[1]);
                    lcNo++;
                    sleep(33);
                }
            }
        }.start();
    }

    void procFailGLs() throws HostNotFoundException {
        new Process(host, host.getName() + "-terminateGMs") {
            public void main(String[] args) throws HostFailureException {
                sleep(4000);
                for (int i=0; i< SimulatorProperties.getNbOfServiceNodes()/2 && !testsToBeTerminated; i++) {
                    if (multicast.gmInfo.size() < 3) {
                        Logger.debug("[Test.failGLs] #GMs: " + multicast.gmInfo.size());
                        sleep(3000);
                        continue;
                    }
                    m = new TestFailGLMsg(name, AUX.glInbox(multicast.glHostname), null, null);
                    m.send();
                    Logger.tmp("[Test.failGLs] GL failure: " + Test.gl.getHost().getName());
                    sleep(1537);
//                    break;
                }
            }
        }.start();
    }

    void procFailGMs() throws HostNotFoundException {
        new Process(host, host.getName() + "-terminateGMs") {
            public void main(String[] args) throws HostFailureException {
                sleep(5000);
                for (int i=0; i<SimulatorProperties.getNbOfServiceNodes()/2 && !testsToBeTerminated; i++) {
                    if (multicast.gmInfo.size() < 3) {
                        Logger.info("[Test.failGMs] #GMs: " + multicast.gmInfo.size());
                        sleep(1777);
                        continue;
                    }
                    gm = new ArrayList<String>(multicast.gmInfo.keySet()).get(0);
                    m = new TestFailGMMsg(name, AUX.gmInbox(gm), null, null);
                    m.send();
                    Logger.tmp("[Test.failGMs] Term. GM: " + gm + ", #GMs: " + multicast.gmInfo.size());
                    sleep(1777);
//                    break;
                }
            }
        }.start();
    }

    static void dispInfo() {
//        if (multicast.gmInfo.isEmpty()) {
//            Logger.debug("[Test.dispGMLCInfo] MUL.gmInfo empty");
//            return;
//        }
        int i = 0, al = 0, gmal = 0;
        Logger.tmp("\n\n[Test.dispInfo] #MUL.gmInfo: " + multicast.gmInfo.size() +
                ", #MUL.lcInfo: " + multicast.lcInfo.size() + ", #Test.gms " + Test.gms.size());
        for (String gm : multicast.gmInfo.keySet()) {
            int l = 0;
            for (String lc : multicast.lcInfo.keySet()) if (multicast.lcInfo.get(lc).gmHost.equals(gm)) l++;
            String gmLeader = "";
            int gml = 0;
            for (String gmn : Test.gms.keySet()) {
                GroupManager gmo = Test.gms.get(gmn);
                if (gmn.equals(gm)) {
                    gmLeader = gmo.glHostname;
                    gml = gmo.lcInfo.size();
                    gmal += gml;
                }
            }
            Logger.tmp("    MUL.GM: " + gm + ", MUL.GM.#LCs: " + l + ", Test.GMLeader: " + gmLeader);
//            Logger.tmp("                         GM.#LCs: " + gml + ", Test.GMLeader: " + gmLeader);
            i++;
            al += l;
        }
        if (gl != null)
            Logger.tmp("    Test.GL: " + gl.host.getName()
                    + ", Test.GL.#GM: " + gl.gmInfo.size() + ", MUL.GM.#LCs: " + al + ", GM.#LCs: " + gmal + "\n");
    }
}
