package org.casc.lang;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ClassWatcher {
    private static WatchService ws;

    static {
        try {
            ws = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        var window = new JFrame("Class Watcher - A Class File Assistance");
        var dimension = Toolkit.getDefaultToolkit().getScreenSize();
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        window.setSize(dimension.width / 2, dimension.height);

        var javapTextArea = new AtomicReference<>(new JTextArea());
        var scrollPane = new JScrollPane(javapTextArea.get());

        javapTextArea.get().setFont(new Font("JetBrainsMono Nerd Font Mono", Font.PLAIN, 12));

        window.add(scrollPane);
        window.setVisible(true);

        var fileChooser = new JFileChooser(System.getProperty("user.dir"));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new FileNameExtensionFilter("Class Files", "class"));
        var resultCode = fileChooser.showOpenDialog(window);

        var classFile = resultCode == JFileChooser.APPROVE_OPTION ? fileChooser.getSelectedFile() : null;

        if (classFile == null) {
            window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
            return;
        }

        var parentFolder = classFile.getParentFile();

        parentFolder.toPath().register(
                ws,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE
        );

        Thread thread = new Thread(() -> {
            while (true) {
                WatchKey wk = null;

                try {
                    wk = ws.take();

                    Thread.sleep(500);

                    for (var event : wk.pollEvents()) {
                        var changedFile = parentFolder.toPath().resolve((Path) event.context());

                        if (Files.exists(changedFile) && changedFile == classFile.toPath()) {
                            var process = execJavap(classFile);

                            process.waitFor();

                            javapTextArea.get().setText("");
                            var output = new BufferedReader(process.exitValue() == 0 ? process.inputReader() : process.errorReader())
                                    .lines()
                                    .collect(Collectors.joining("\n"));
                            javapTextArea.get().setText(output);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (wk != null) wk.reset();
                }
            }
        });

        var process = execJavap(classFile);

        process.waitFor();

        var output = new BufferedReader(process.exitValue() == 0 ? process.inputReader() : process.errorReader())
                .lines()
                .collect(Collectors.joining("\n"));
        javapTextArea.get().setText(output);
        thread.start();
    }

    private static Process execJavap(File file) throws IOException {
        var isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        var fileName = file.getName().replaceAll("[.][^.]+$", "");

        return Runtime.getRuntime().exec(
                isWindows ? "cmd.exe /c cd " + file.getParent() + " && javap -v " + fileName
                        : "sh -c cd " + file.getParent() + " && javap -v " + fileName
        );
    }
}
