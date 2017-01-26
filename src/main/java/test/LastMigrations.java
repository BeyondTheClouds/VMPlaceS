package test;

import org.simgrid.msg.Msg;
import scala.xml.Null;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum LastMigrations {
    INSTANCE;

    private List<HashMap<Pair, Double>> iterations;

    LastMigrations() {
        try {
            iterations = new ArrayList<>();
            int i = 1;
            Pattern p = Pattern.compile("(.*): (.*) -> (.*) \\(([\\d,]+)\\)");

            File f = new File("/tmp/ffd/migrations/" + i + ".txt");
            while (f.exists()) {
                HashMap<Pair, Double> pairs = new HashMap<>();

                try {
                    BufferedReader reader = new BufferedReader(new FileReader(f));
                    String line = null;

                    while ((line = reader.readLine()) != null) {
                        Matcher m = p.matcher(line);
                        if (m.matches()) {
                            Pair pair = new Pair(m.group(1), m.group(2), m.group(3));
                            pairs.put(pair, Double.parseDouble(m.group(4).replace(',', '.')));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                iterations.add(pairs);
                i++;
                f = new File("/tmp/ffd/migrations/" + i + ".txt");
            }
        } catch(Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            System.exit(23);
        }
    }

    public double get(int iteration, String vmName, String src, String dst) {
        Pair p = new Pair(vmName, src, dst);

        Double duration = null;
        if(iterations.get(iteration - 1) == null) {
            Msg.info("No previous iteration for " + iteration);
            duration = -1.0;
        }

        duration = iterations.get(iteration - 1).get(p);
        if(duration == null)
            return -1.0;
        else
            return duration;
    }

    class Pair {
        String vmName = null;
        String src = null;
        String dst = null;

        public Pair(String vmName, String src, String dst) {
            this.vmName = vmName;
            this.src = src;
            this.dst = dst;
        }

        public boolean equals(Object o) {
            if(!(o instanceof Pair))
                return false;

            Pair p = (Pair) o;
            return p.vmName.equals(vmName) && p.src.equals(src) && p.dst.equals(dst);
        }
    }
}
