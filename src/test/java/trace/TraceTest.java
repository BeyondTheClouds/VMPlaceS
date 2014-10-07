//package trace;
//
//import org.junit.Test;
//
//public class TraceTest {
//
//    private double datetime = 0;
//
//    class TraceImplForTesting extends TraceImpl {
//
//        @Override
//        protected double now() {
//            return datetime;
//        }
//
//    }
//
//    public TraceImplForTesting trace = new TraceImplForTesting();
//
//    @Test
//    public void testHostStateDeclare() {
//
//        datetime = 345.1;
//        trace.hostStateDeclare("VIOLATION");
//        datetime = 348.5;
//        trace.hostPopState("node1", "VIOLATION");
//
//    }
//
//
//    @Test
//    public void testHostPushState() {
//
//        datetime = 345.1;
//        trace.hostPushState("node1", "VIOLATION", "VIOLATED");
//        datetime = 348.5;
//        trace.hostPopState("node1", "VIOLATION");
//
//    }
//
//    @Test
//    public void testHostVariableDeclare() {
//
//        datetime = 345.1;
//        trace.hostVariableDeclare("VAR1");
//        datetime = 348.5;
//        trace.hostVariableDeclare("node1", "VAR2");
//
//    }
//
//    @Test
//    public void testHostVariableAdd() {
//
//        datetime = 345.1;
//        trace.hostVariableAdd("node1", "LOAD", 1);
//
//    }
//}
