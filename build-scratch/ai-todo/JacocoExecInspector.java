import java.io.File;
import org.jacoco.core.data.ExecFileLoader;

public class JacocoExecInspector {
  public static void main(String[] args) throws Exception {
    File execFile = new File(args.length == 0 ? "target/jacoco.exec" : args[0]);
    ExecFileLoader loader = new ExecFileLoader();
    loader.load(execFile);
    var sessions = loader.getSessionInfoStore().getInfos();
    var classes = loader.getExecutionDataStore().getContents();
    System.out.println("exec=" + execFile.getAbsolutePath());
    System.out.println("sessions=" + sessions.size());
    if (!sessions.isEmpty()) {
      var first = sessions.get(0);
      var last = sessions.get(sessions.size() - 1);
      System.out.println("session.first=" + first.getId() + " " + first.getStartTimeStamp() + ".." + first.getDumpTimeStamp());
      System.out.println("session.last=" + last.getId() + " " + last.getStartTimeStamp() + ".." + last.getDumpTimeStamp());
    }
    System.out.println("classes=" + classes.size());
    long probes = 0;
    long probesCovered = 0;
    for (var data : classes) {
      boolean[] p = data.getProbes();
      probes += p.length;
      for (boolean b : p) if (b) probesCovered++;
    }
    System.out.println("probes=" + probes);
    System.out.println("probesCovered=" + probesCovered);
  }
}