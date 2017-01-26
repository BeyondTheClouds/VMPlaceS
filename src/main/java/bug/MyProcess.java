package bug;

import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.Host;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;

import java.util.Comparator;
import java.util.Random;

public class MyProcess extends Process {
    public MyProcess(Host h, String name, String[] args) {
        super(h, name, args);
    }

    @Override
    public void main(String[] strings) throws MsgException {
        XVMComparator c = new XVMComparator(true, true);

        Host h = Host.getByName("node1");
        XHost xh = new XHost(h, 10000, 8, 65468, 65468, null);

        Random r = new Random(42);
        for(int i=0; i < 20; i++) {
            int ram = r.nextInt(8000);
            int bw = r.nextInt(10000);
            int dpi = r.nextInt(100);
            XVM vm1 = new XVM(xh, "vm-" + i + 'a', 1, ram, bw, null, 0, 0, dpi);
            XVM vm2 = new XVM(xh, "vm-" + i + 'b', 1, ram, bw, null, 0, 0, dpi);

            System.out.println(String.valueOf(c.compare(vm1, vm2)));
            System.out.println(String.valueOf(c.compare(vm2, vm1)));
        }
    }
}

class XVMComparator implements Comparator<XVM> {
    private int factor = 1;
    private boolean useLoad = false;

    public XVMComparator(boolean useLoad) {
        this(false, useLoad);
    }

    public XVMComparator(boolean decreasing, boolean useLoad) {
        if(decreasing)
            this.factor = -1;

        this.useLoad = useLoad;
    }

    @Override
    public int compare(XVM h1, XVM h2) {
        if(useLoad && h1.getLoad() != h2.getLoad()) {
            return (int) Math.round(factor * h1.getLoad() - h2.getLoad());
        }

        if(h1.getCPUDemand() != h2.getCPUDemand()) {
            return factor * (int) Math.round((h1.getCPUDemand() - h2.getCPUDemand()));
        }

        if(h1.getMemSize() != h2.getMemSize())
            return factor * (h1.getMemSize() - h2.getMemSize());


        if(h1.getName().equals(h2.getName()))
            return 0;
        else
            return -1;
    }
}
