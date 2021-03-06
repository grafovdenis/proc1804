import lombok.NonNull;
import proc.Processor;
import proc.help.Command;
import proc.help.Flags;
import system.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

// TODO
public class Runner {
    private Reader inputReader;
    private StateWriter stateWriter;
    private String help;
    private String welcome;
    private Processor processor;
    private Map<Integer, String> comments;

    private Runner(@NonNull File file) throws IOException {
        prepare(file);
        Command[] commands = loadCommands();
        stateWriter.setComments(comments);
        processor = new Processor(null);
        processor.storeProgram(commands);
    }

    public static void main(String[] args) throws IOException, NoSuchFieldException, IllegalAccessException {
        if (args.length < 1) throw new IllegalArgumentException("Please pass the path to the configuration file");
        UpdatesChecker.checkForUpdates();
        Runner runner = new Runner(new File(args[0]));
        runner.run();
    }

    private void run() throws IOException, NoSuchFieldException, IllegalAccessException {
        final Scanner scanner = new Scanner(System.in);
        final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(System.out);
        List<ProcState> statesHistory = new ArrayList<>();
        statesHistory.add(ProcState.of(processor));
        String line;
        System.out.println(welcome);
        System.out.println(help);
        System.out.print("command: ");
        while (!("exit".equals(line = scanner.nextLine()) || "e".equals(line))) {
            if ("help".equals(line) || "h".equals(line)) {
                System.out.println("<--- help");
                System.out.println(help);
            } else if ("state".equals(line) || "s".equals(line)) {
                System.out.println("<--- state");
                stateWriter.writeHeader(outputStreamWriter);

                final ProcState state = ProcState.of(processor);
                final int clk = state.getClkCounter();
                Flags flags = state.getFlags();
                if (clk > 1) flags = statesHistory.get(clk - 1).getFlags();
                stateWriter.write(state, outputStreamWriter, flags);

                stateWriter.writeFooter(outputStreamWriter);
            } else if ("stateHistory".equals(line) || "sh".equals(line)) {
                System.out.println("<--- stateHistory");
                stateWriter.writeHeader(outputStreamWriter);

                for (int i = 0; i < statesHistory.size(); i++) {
                    final ProcState state = statesHistory.get(i);
                    Flags flags = state.getFlags();
                    if (i > 0) flags = statesHistory.get(i - 1).getFlags();
                    stateWriter.write(state, outputStreamWriter, flags);
                }
                stateWriter.writeFooter(outputStreamWriter);
            } else if ("clk".equals(line) || "c".equals(line)) {
                System.out.println("<--- clk");
                processor.clk();
                statesHistory.add(ProcState.of(processor));
            } else if (Pattern.matches("^(c|clk) [0-9]+", line)) {
                int times = Integer.valueOf(line.split(" ")[1]);
                System.out.println("<--- clk " + times + " times");
                for (int i = 0; i < times; i++) {
                    processor.clk();
                    statesHistory.add(ProcState.of(processor));
                }
            } else {
                System.out.println("<--- NO command");
            }
            System.out.print("command: ");
        }
        System.out.println("<--- exit");
        stateWriter.writeHeader();

        for (int i = 0; i < statesHistory.size(); i++) {
            final ProcState state = statesHistory.get(i);
            Flags flags = state.getFlags();
            if (i > 0) flags = statesHistory.get(i - 1).getFlags();
            stateWriter.write(state, flags);
        }

        stateWriter.writeFooter();
        stateWriter.close();
    }

    private void prepare(@NonNull File file) throws IOException {
        final var properties = new Properties();
        properties.load(new FileReader(file));
        final String inputFilePath = properties.getProperty("input");
        final String outputFilePath = properties.getProperty("output");
        final String writeSettingsFilePath = properties.getProperty("writer");
        final String helpFilePath = properties.getProperty("help");
        final String welcomeFilePath = properties.getProperty("welcome");
        Writer outputWriter = new FileWriter(new File(outputFilePath));
        inputReader = new InputStreamReader(new FileInputStream(inputFilePath), StandardCharsets.UTF_8);
        stateWriter = new CoolWriter(new File(writeSettingsFilePath), outputWriter);
        help = Files.readAllLines(Path.of(helpFilePath)).stream().reduce("", (a, b) -> a + "\n" + b, String::concat);
        welcome = Files.readAllLines(Path.of(welcomeFilePath)).stream().reduce("", (a, b) -> a + "\n" + b, String::concat);
    }

    private Command[] loadCommands() throws IOException {
        comments = new HashMap<>();
        CommandsLoader commandsLoader = new FileCommandsLoader();
        commandsLoader.setCommentsMap(comments);
        return commandsLoader.loadCommands(inputReader);
    }
}