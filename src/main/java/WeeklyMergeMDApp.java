import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class WeeklyMergeMDApp {
    private static final Logger LOG = LogManager.getLogger(WeeklyMergeMDApp.class);
    private final AppConfig config;
    private final Messages messages;
    private final JFrame frame;
    private final DefaultListModel<Path> model = new DefaultListModel<>();
    private final JList<Path> list = new JList<>(model);
    private final LinkedHashSet<Path> selectionOrder = new LinkedHashSet<>();
    private final ReportMerger merger = new ReportMerger();
    private final JButton merge;
    private final JButton refresh;
    public WeeklyMergeMDApp(AppConfig config, Messages messages) {
        this.config = config; this.messages = messages;
        frame = new JFrame(messages.get("app.title"));
        merge = new JButton(messages.get("merge")); refresh = new JButton(messages.get("refresh"));
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel top = new JPanel(new GridLayout(0, 1, 4, 4));
        top.add(new JLabel(messages.get("input", config.inputDirectory())));
        top.add(new JLabel(messages.get("output", config.outputDirectory())));
        top.add(new JLabel(messages.get("hint")));
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(l, ((Path)value).getFileName().toString(), index, selected, focus);
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                List<Path> selected = list.getSelectedValuesList();
                selectionOrder.retainAll(selected); selectionOrder.addAll(selected);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.add(refresh); buttons.add(merge);
        JPanel content = new JPanel(new BorderLayout(8, 8)); content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(top, BorderLayout.NORTH); content.add(new JScrollPane(list), BorderLayout.CENTER); content.add(buttons, BorderLayout.SOUTH);
        frame.setContentPane(content); frame.setSize(850, 480); frame.setLocationRelativeTo(null);
        refresh.addActionListener(e -> reload()); merge.addActionListener(e -> startMerge());
    }
    private void error(Throwable e) {
        LOG.error("Report merge failed", e);
        JOptionPane.showMessageDialog(frame, e.getMessage(), messages.get("error"), JOptionPane.ERROR_MESSAGE);
    }
    private void reload() {
        try { List<Path> paths = ReportMerger.listReports(config.inputDirectory()); model.clear(); selectionOrder.clear(); paths.forEach(model::addElement); }
        catch (Exception e) { error(e); }
    }
    private void startMerge() {
        List<Path> inputs = selectionOrder.isEmpty() ? Collections.list(model.elements()) : List.copyOf(selectionOrder);
        try {
            if (inputs.isEmpty()) throw new IllegalStateException(messages.get("empty"));
            if (!Files.isDirectory(config.outputDirectory())) throw new IllegalStateException(messages.get("missing.output", config.outputDirectory()));
            ReportMerger.template(config.outputDirectory());
        } catch (Exception e) { error(e); return; }
        Path target = merger.resultPath(config.outputDirectory());
        boolean overwrite = Files.exists(target);
        if (overwrite && JOptionPane.showConfirmDialog(frame, messages.get("overwrite", target), messages.get("app.title"), JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        JDialog dialog = new JDialog(frame, messages.get("progress.title"), true);
        JProgressBar progress = new JProgressBar(SwingConstants.HORIZONTAL, 0, 100);
        progress.setStringPainted(true); progress.setValue(0); progress.setString("0%");
        progress.setPreferredSize(new Dimension(540, 24));
        progress.getAccessibleContext().setAccessibleName(messages.get("progress.title"));
        JLabel status = new JLabel(messages.get("progress.prepare")); JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16)); panel.add(status, BorderLayout.NORTH); panel.add(progress, BorderLayout.CENTER);
        dialog.setContentPane(panel); dialog.setSize(580, 130); dialog.setLocationRelativeTo(frame); dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        merge.setEnabled(false); refresh.setEnabled(false);
        new SwingWorker<Path, Void>() {
            @Override protected Path doInBackground() throws Exception {
                return merger.merge(inputs, config.outputDirectory(), overwrite, (percent, name) -> SwingUtilities.invokeLater(() -> {
                    progress.setValue(percent); progress.setString(percent + "%");
                    status.setText(percent == 100 ? messages.get("progress.complete")
                            : name.isEmpty() ? messages.get(percent < 90 ? "progress.merge" : "progress.write")
                            : messages.get("progress.read", name));
                }));
            }
            @Override protected void done() {
                dialog.dispose(); merge.setEnabled(true); refresh.setEnabled(true);
                try { JOptionPane.showMessageDialog(frame, messages.get("success", get())); }
                catch (Exception e) { error(e.getCause() == null ? e : e.getCause()); }
            }
        }.execute();
        dialog.setVisible(true);
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                WeeklyMergeMDApp app = new WeeklyMergeMDApp(AppConfig.load(), new Messages()); app.frame.setVisible(true); app.reload();
            } catch (Exception e) { LOG.error("Startup failed", e); JOptionPane.showMessageDialog(null, e.getMessage(), "시작 오류", JOptionPane.ERROR_MESSAGE); }
        });
    }
}
