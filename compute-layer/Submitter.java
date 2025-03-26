import java.time.LocalDateTime;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

class Submitter {
  public static void main(String[] args) throws IOException {
    var now = LocalDateTime.now();
    Long hours = 1L;
    if (args.length > 2) {
        hours = Long.parseLong(args[2]);
    }
    
    String t;
    for (int i = 0; i < Integer.parseInt(args[1]); ++i) {
        t = now.plus(Duration.ofHours(i * hours)).plus(Duration.ofMinutes(2)).format(DateTimeFormatter.ofPattern("HH:mm"));
        var cmd = String.format(
            "echo java -cp mandelbrot-generator/target/scala-3.5.0/mandelbrot-generator-assembly-0.1.0-SNAPSHOT.jar rrt.mandelbrot producer %s | at %s",
            args[0],
            t
        );

        var p = new ProcessBuilder("bash", "-c", cmd);
        // p.redirectError(Redirect.INHERIT);
        p.redirectOutput(Redirect.INHERIT);
        p.start();
    }
  }
}
