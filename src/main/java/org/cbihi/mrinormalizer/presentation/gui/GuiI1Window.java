package org.cbihi.mrinormalizer.presentation.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;

/** Classic single-window desktop shell. The accepted conversion engines remain untouched. */
public final class GuiI1Window extends JFrame {
    private static final Color TEXT = new Color(31, 31, 31);
    private static final Color MUTED = new Color(103, 103, 103);
    private static final Color EDGE = new Color(192, 192, 192);
    private static final Color BAR = new Color(245, 245, 245);
    private static final Color SURFACE = Color.WHITE;
    private static final Border FIELD_BORDER = BorderFactory.createLineBorder(new Color(187, 187, 187));

    private final GuiI1Scanner scanner;
    private final GuiI1Execution execution;
    private final JTabbedPane directionTabs = new JTabbedPane(JTabbedPane.TOP);
    private JPanel sharedContent;
    private final HintField sourceField = new HintField();
    private final HintField outputField = new HintField();
    private final JButton sourceBrowse = new JButton("Browse...");
    private final JButton outputBrowse = new JButton("Browse...");
    private final JButton convertButton = new JButton("Convert");
    private final JButton closeButton = new JButton("Close");
    private final JLabel sourceLabel = new JLabel("Input folder:");
    private final JLabel inspectionHeading = new JLabel("Selected series");
    private final JLabel inspectionCount = new JLabel("0 selected");
    private final JLabel activityState = new JLabel("Idle");
    private final JLabel status = new JLabel("Ready");
    private final JLabel footerNote = new JLabel("Outputs are independently verified by the conversion backend.");
    private final JLabel evaluationNote = new JLabel(
            "Non-clinical evaluation template; original acquisition metadata is not reconstructed.");
    private final JCheckBox evaluationAck = new JCheckBox(
            "I acknowledge that generated DICOM metadata uses evaluation assumptions.");
    private final JPanel evaluationPanel = new JPanel(new BorderLayout(4, 4));
    private final JTextArea activity = new JTextArea();
    private final JProgressBar progress = new JProgressBar();

    private final DefaultTableModel rows = new DefaultTableModel(
            new Object[]{"Use", "Directory", "Series Instance UID", "Inspection"}, 0) {
        @Override public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : String.class;
        }
        @Override public boolean isCellEditable(int row, int column) {
            return !busy && column == 0 && scan != null
                    && scan.kind() == GuiI1Scanner.InputKind.DICOM_PREPARED_PARENT
                    && row >= 0 && row < scan.candidates().size()
                    && scan.candidates().get(row).eligible();
        }
    };
    private final JTable candidates = new JTable(rows) {
        @Override public String getToolTipText(MouseEvent event) {
            int view = rowAtPoint(event.getPoint());
            if (scan != null && view >= 0) {
                int index = convertRowIndexToModel(view);
                if (index < scan.candidates().size()) return scan.candidates().get(index).finding();
            }
            return super.getToolTipText(event);
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getRowCount() == 0) {
                String text = reverse() ? "No NIfTI inputs loaded" : "No input series loaded";
                FontMetrics metrics = graphics.getFontMetrics();
                graphics.setColor(new Color(142, 142, 142));
                graphics.drawString(text, Math.max(8, (getWidth() - metrics.stringWidth(text)) / 2),
                        Math.max(metrics.getAscent(), (getHeight() + metrics.getAscent()) / 2));
            }
        }
    };
    private final JPanel tablePane = new JPanel(new BorderLayout());
    private final JScrollPane tableScroll = new JScrollPane(candidates);

    private volatile boolean busy;
    private GuiI1Scanner.ScanResult scan;
    private Path completedOutputRoot;
    private String verifiedSelection;
    private boolean suppressSourceChange;

    public GuiI1Window() { this(new GuiI1Scanner(), new GuiI1Execution()); }
    GuiI1Window(GuiI1Scanner scanner, GuiI1Execution execution) {
        super("MRI Volume Normalizer");
        this.scanner = scanner;
        this.execution = execution;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1060, 725);
        setMinimumSize(new Dimension(830, 600));
        setLocationByPlatform(true);
        build();
        wire();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { requestClose(); }
        });
        directionChanged();
        updateActionState();
    }

    private static JLabel section(String label) {
        JLabel title = new JLabel(label);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setForeground(TEXT);
        return title;
    }
    private static JPanel horizontalLine() {
        JPanel line = new JPanel();
        line.setBackground(EDGE);
        line.setPreferredSize(new Dimension(1, 1));
        return line;
    }
    private static void tint(Component component, Color foreground) { component.setForeground(foreground); }
    private static GridBagConstraints gbc(int x, int y, double weight) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.weightx = weight;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 0, 7, 10);
        return c;
    }
    private JPanel inputFields() {
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        fields.add(sourceLabel, gbc(0, 0, 0));
        fields.add(sourceField, gbc(1, 0, 1));
        GridBagConstraints browse1 = gbc(2, 0, 0);
        browse1.insets.right = 0;
        fields.add(sourceBrowse, browse1);
        fields.add(new JLabel("Output folder:"), gbc(0, 1, 0));
        fields.add(outputField, gbc(1, 1, 1));
        GridBagConstraints browse2 = gbc(2, 1, 0);
        browse2.insets.right = 0;
        fields.add(outputBrowse, browse2);
        sourceField.setHint("Select DICOM file or folder...");
        outputField.setHint("Select a qualified output directory...");
        sourceField.setToolTipText("Choose a DICOM folder, including nested or mixed conventional MR series; select up to 16 series");
        outputField.setToolTipText("Choose an existing separate destination directory");
        sourceBrowse.setPreferredSize(new Dimension(104, 29));
        outputBrowse.setPreferredSize(new Dimension(104, 29));
        return fields;
    }
    private JPanel heading(JLabel left, JLabel right) {
        JPanel h = new JPanel(new BorderLayout());
        h.setOpaque(false);
        h.add(left, BorderLayout.WEST);
        tint(right, MUTED);
        h.add(right, BorderLayout.EAST);
        return h;
    }
    private JPanel topContents() {
        JPanel top = new JPanel(new BorderLayout(0, 9));
        top.setOpaque(false);
        top.add(section("Conversion setup"), BorderLayout.NORTH);
        top.add(inputFields(), BorderLayout.CENTER);
        return top;
    }
    private JPanel inspection() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        inspectionHeading.setFont(inspectionHeading.getFont().deriveFont(Font.BOLD));
        inspectionHeading.setForeground(TEXT);
        panel.add(heading(inspectionHeading, inspectionCount), BorderLayout.NORTH);
        tablePane.setBackground(SURFACE);
        tablePane.setBorder(FIELD_BORDER);
        candidates.setRowHeight(24);
        candidates.setFillsViewportHeight(true);
        candidates.setGridColor(new Color(234, 234, 234));
        candidates.setShowGrid(false);
        candidates.setShowHorizontalLines(true);
        candidates.getTableHeader().setReorderingAllowed(false);
        candidates.getTableHeader().setPreferredSize(new Dimension(0, 29));
        TableColumnModel columns = candidates.getColumnModel();
        columns.getColumn(0).setMinWidth(46);
        columns.getColumn(0).setMaxWidth(58);
        columns.getColumn(1).setPreferredWidth(190);
        columns.getColumn(2).setPreferredWidth(320);
        columns.getColumn(3).setPreferredWidth(115);
        columns.getColumn(3).setMaxWidth(160);
        DefaultTableCellRenderer left = new DefaultTableCellRenderer();
        left.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 4));
        for (int i = 1; i < columns.getColumnCount(); i++)
            columns.getColumn(i).setCellRenderer(left);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableScroll.setBackground(SURFACE);
        tablePane.add(tableScroll, BorderLayout.CENTER);
        panel.add(tablePane, BorderLayout.CENTER);
        return panel;
    }
    private JPanel activityPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.add(heading(section("Activity"), activityState), BorderLayout.NORTH);
        activity.setEditable(false);
        activity.setLineWrap(true);
        activity.setWrapStyleWord(true);
        activity.setFont(activity.getFont().deriveFont(Font.PLAIN, 12f));
        activity.setBackground(SURFACE);
        activity.setForeground(MUTED);
        activity.setMargin(new Insets(9, 11, 9, 11));
        activity.setText("Ready.\nSelect a source folder to inspect DICOM series.");
        JScrollPane logScroll = new JScrollPane(activity);
        logScroll.setBorder(FIELD_BORDER);
        logScroll.setPreferredSize(new Dimension(1, 86));
        panel.add(logScroll, BorderLayout.CENTER);
        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(1, 6));
        panel.add(progress, BorderLayout.SOUTH);
        return panel;
    }
    private JPanel footActions() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        tint(footerNote, MUTED);
        footer.add(footerNote, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        closeButton.setPreferredSize(new Dimension(88, 32));
        convertButton.setPreferredSize(new Dimension(116, 32));
        convertButton.setFont(convertButton.getFont().deriveFont(Font.BOLD));
        buttons.add(closeButton);
        buttons.add(convertButton);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }
    private void build() {
        // The frame's native title bar is the only header. No toolbar, hero, or empty top panel.
        setLayout(new BorderLayout());
        JPanel surroundings = new JPanel(new BorderLayout());
        surroundings.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12));
        surroundings.setBackground(BAR);
        directionTabs.setFocusable(false);
        JPanel forwardPage = new JPanel(new BorderLayout());
        JPanel reversePage = new JPanel(new BorderLayout());
        forwardPage.setBackground(SURFACE);
        reversePage.setBackground(SURFACE);
        directionTabs.addTab("DICOM → NIfTI", forwardPage);
        directionTabs.addTab("NIfTI → DICOM", reversePage);

        JPanel content = new JPanel(new BorderLayout(0, 13));
        content.setBackground(SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 17, 18));
        JPanel initial = new JPanel(new BorderLayout(0, 8));
        initial.setOpaque(false);
        initial.add(topContents(), BorderLayout.NORTH);

        evaluationPanel.setBackground(new Color(250, 250, 250));
        evaluationPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(219, 219, 219)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        evaluationNote.setForeground(MUTED);
        evaluationPanel.add(evaluationNote, BorderLayout.NORTH);
        evaluationAck.setOpaque(false);
        evaluationPanel.add(evaluationAck, BorderLayout.SOUTH);
        initial.add(evaluationPanel, BorderLayout.SOUTH);
        content.add(initial, BorderLayout.NORTH);

        JPanel middle = new JPanel(new BorderLayout(0, 18));
        middle.setOpaque(false);
        middle.add(inspection(), BorderLayout.CENTER);
        middle.add(activityPanel(), BorderLayout.SOUTH);
        content.add(middle, BorderLayout.CENTER);
        content.add(footActions(), BorderLayout.SOUTH);
        sharedContent = content;
        forwardPage.add(sharedContent, BorderLayout.CENTER);
        // The native tab container owns the entire workspace, so there is no blank
        // tab-content band. Switching only moves the presentation panel between tabs.
        surroundings.add(directionTabs, BorderLayout.CENTER);
        add(surroundings, BorderLayout.CENTER);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(BAR);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, EDGE),
                BorderFactory.createEmptyBorder(5, 16, 5, 16)));
        status.setForeground(MUTED);
        statusBar.add(status, BorderLayout.WEST);
        JLabel laboratory = new JLabel("CBI-HI Laboratory, IITM");
        laboratory.setForeground(TEXT);
        laboratory.setFont(laboratory.getFont().deriveFont(Font.PLAIN, 11f));
        statusBar.add(laboratory, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);
    }
    private GuiI1Scanner.Direction selectedDirection() {
        return directionTabs.getSelectedIndex() == 0
                ? GuiI1Scanner.Direction.DICOM_TO_NIFTI : GuiI1Scanner.Direction.NIFTI_TO_DICOM;
    }
    private boolean reverse() { return selectedDirection() == GuiI1Scanner.Direction.NIFTI_TO_DICOM; }
    private void directionChanged() {
        sourceLabel.setText(reverse() ? "Input file/folder:" : "Input folder:");
        sourceField.setToolTipText(reverse() ? "Select one .nii/.nii.gz file or a flat directory"
                : "Select a DICOM folder; series and instance counts are discovered automatically");
        inspectionHeading.setText(reverse() ? "Selected NIfTI inputs" : "Selected series");
        sourceField.setHint(reverse() ? "Select a .nii/.nii.gz file or input folder..."
                : "Select a folder containing DICOM series...");
        outputField.setHint(reverse() ? "Select DICOM output parent directory..."
                : "Select a qualified output directory...");
        candidates.getColumnModel().getColumn(1).setHeaderValue(reverse() ? "File" : "Directory");
        candidates.getColumnModel().getColumn(2).setHeaderValue(reverse() ? "Format" : "Series Instance UID");
        candidates.getTableHeader().repaint();
        candidates.repaint();
        evaluationPanel.setVisible(reverse());
        if (!reverse()) evaluationAck.setSelected(false);
        footerNote.setText(reverse()
                ? "Verified outputs use non-clinical evaluation metadata, not original acquisition attributes."
                : "Outputs are independently verified by the conversion backend.");
        activity.setText("Ready.\n" + (reverse()
                ? "Select a NIfTI file or folder to inspect inputs."
                : "Select a source folder to inspect DICOM series."));
        updateActionState();
    }
    private void resetScan() {
        if (busy) return;
        scan = null;
        rows.setRowCount(0);
        completedOutputRoot = null;
        candidates.repaint();
        inspectionCount.setText("0 selected");
        activityState.setText("Idle");
        status.setText("Ready");
        activity.setText("Ready.\n" + (reverse()
                ? "Select a NIfTI file or folder to inspect inputs."
                : "Select a source folder to inspect DICOM series."));
        updateActionState();
    }
    private List<Integer> selectedIndices() {
        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < rows.getRowCount(); i++)
            if (Boolean.TRUE.equals(rows.getValueAt(i, 0))) selected.add(i);
        return selected;
    }
    private String selectionSummary() {
        if (scan == null) return "0 selected";
        if (reverse()) return selectedIndices().size() + " selected of " + rows.getRowCount();
        long instances = scan.candidates().stream().mapToLong(GuiI1Scanner.Candidate::instanceCount).sum();
        return scan.candidates().size() + " series · " + instances + " instances · "
                + selectedIndices().size() + " selected";
    }
    private boolean validDestinationPresent() {
        try { return !outputField.getText().isBlank() &&
                Files.isDirectory(Path.of(outputField.getText().trim())); }
        catch (RuntimeException badPath) { return false; }
    }
    private String selectionIdentity() {
        return scan==null?null:scan.direction()+"|"+scan.input()+"|"+outputField.getText().trim()+"|"+selectedIndices();
    }
    private void updateActionState() {
        convertButton.setEnabled(!busy && !java.util.Objects.equals(verifiedSelection,selectionIdentity()) && scan != null && !selectedIndices().isEmpty()
                && selectedIndices().stream().allMatch(i -> scan.candidates().get(i).eligible())
                && (reverse() || selectedIndices().size() <= 16)
                && validDestinationPresent() && (!reverse() || evaluationAck.isSelected()));
        closeButton.setEnabled(!busy);
    }
    private void setBusy(boolean value) {
        busy = value;
        directionTabs.setEnabled(!value);
        sourceBrowse.setEnabled(!value);
        outputBrowse.setEnabled(!value);
        sourceField.setEnabled(!value);
        outputField.setEnabled(!value);
        candidates.setEnabled(!value);
        evaluationAck.setEnabled(!value);
        progress.setVisible(value);
        if(value)activityState.setText("Working");
        updateActionState();
    }
    private void requestClose() {
        if (busy) JOptionPane.showMessageDialog(this,
                "A backend operation is running. Keep the application open until it finishes.",
                "Conversion in progress", JOptionPane.WARNING_MESSAGE);
        else dispose();
    }
    private void browse(boolean isSource) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(isSource ? "Select source" : "Select existing output folder");
        chooser.setFileSelectionMode(isSource
                ? JFileChooser.FILES_AND_DIRECTORIES : JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        if (isSource) {
            suppressSourceChange = true;
            try { sourceField.setText(chooser.getSelectedFile().getAbsolutePath()); }
            finally { suppressSourceChange = false; }
            resetScan();
            inspectSource();
        } else {
            outputField.setText(chooser.getSelectedFile().getAbsolutePath());
            updateActionState();
        }
    }
    private void wire() {
        directionTabs.addChangeListener(e -> {
            if (busy) return;
            suppressSourceChange = true;
            try {
                JPanel page = (JPanel) directionTabs.getSelectedComponent();
                page.add(sharedContent, BorderLayout.CENTER);
                page.revalidate();
                page.repaint();
                sourceField.setText("");
                resetScan();
                directionChanged();
            } finally { suppressSourceChange = false; }
        });
        sourceBrowse.addActionListener(e -> browse(true));
        outputBrowse.addActionListener(e -> browse(false));
        convertButton.addActionListener(e -> convert());
        closeButton.addActionListener(e -> requestClose());
        evaluationAck.addActionListener(e -> updateActionState());
        rows.addTableModelListener(e -> {
            if (scan != null && !busy) {
                inspectionCount.setText(selectionSummary());
                updateActionState();
            }
        });
        sourceField.getDocument().addDocumentListener(new TextChange(() -> {
            if (!suppressSourceChange) resetScan();
        }));
        outputField.getDocument().addDocumentListener(new TextChange(() -> {
            completedOutputRoot = null;
            if (!busy) {
                status.setText("Ready");
                activity.setText("Destination changed.\nBackend qualification will run during conversion.");
            }
            updateActionState();
        }));
        sourceField.addActionListener(e -> inspectSource());
        sourceField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                if (!busy && scan == null && !sourceField.getText().isBlank()) inspectSource();
            }
        });
    }
    private void inspectSource() {
        if (busy) return;
        Path input;
        try {
            if (sourceField.getText().isBlank()) throw new IllegalArgumentException();
            input = Path.of(sourceField.getText().trim());
        } catch (RuntimeException invalid) {
            activity.setText("Select a valid input file or folder.");
            status.setText("Input required");
            return;
        }
        GuiI1Scanner.Direction selectedMode = selectedDirection();
        resetScan();
        setBusy(true);
        status.setText("Inspecting input");
        activity.setText("Inspecting supported candidates...\nFinal integrity checks are performed by the conversion backend.");
        new SwingWorker<GuiI1Scanner.ScanResult, Void>() {
            @Override protected GuiI1Scanner.ScanResult doInBackground() throws Exception {
                return scanner.scan(selectedMode, input);
            }
            @Override protected void done() {
                try {
                    GuiI1Scanner.ScanResult result = get();
                    scan = result;
                    boolean checkboxSelected = reverse() || result.candidates().size() == 1;
                    rows.setRowCount(0);
                    for (var candidate : result.candidates()) {
                        String name = result.direction() == GuiI1Scanner.Direction.DICOM_TO_NIFTI
                                ? candidate.childName() == null ? candidate.label() : candidate.childName()
                                : candidate.memberNames().getFirst();
                        String identity = result.direction() == GuiI1Scanner.Direction.DICOM_TO_NIFTI
                                ? candidate.seriesUid() : "NIfTI-1";
                        boolean usable = checkboxSelected && candidate.eligible();
                        String inspection = result.direction() == GuiI1Scanner.Direction.DICOM_TO_NIFTI
                                ? candidate.instanceCount() + " · "
                                        + (candidate.eligible() ? "Preview only" : "Not executable")
                                : "Preview only";
                        rows.addRow(new Object[]{usable, name, identity, inspection});
                    }
                    inspectionCount.setText(selectionSummary());
                    long blocked = result.candidates().stream().filter(c -> !c.eligible()).count();
                    activity.setText("Discovered " + rows.getRowCount() + " candidate(s).\n"
                            + (reverse() ? "EBO directory mode includes every listed input."
                            : blocked + " series not executable under current F9/FBO limits or folder layout. "
                                    + "Hover over a row for details. Backend validation remains authoritative."));
                    status.setText("Inspection complete");
                } catch (Exception unavailable) {
                    scan = null;
                    rows.setRowCount(0);
                    activity.setText("Input inspection refused.\nCheck the supported files and input access.");
                    status.setText("Input rejected");
                } finally {
                    setBusy(false);
                    activityState.setText(status.getText());
                }
            }
        }.execute();
    }
    private void convert() {
        if (busy || scan == null || !validDestinationPresent()
                || selectedIndices().isEmpty() || reverse() && !evaluationAck.isSelected()) return;
        GuiI1Scanner.ScanResult snapshot = scan;
        List<Integer> selected = List.copyOf(selectedIndices());
        Path output;
        try { output = Path.of(outputField.getText().trim()); }
        catch (RuntimeException invalid) { status.setText("Invalid output directory"); return; }
        final String attempt=selectionIdentity();
        setBusy(true);
        activity.setText("Conversion started.\n");
        status.setText("Converting");
        new SwingWorker<GuiI1Execution.Outcome, String>() {
            @Override protected GuiI1Execution.Outcome doInBackground() throws Exception {
                return execution.executeForGui(snapshot, selected, output, event -> publish(event));
            }
            @Override protected void process(List<String> events) {
                for(String line:events)activity.append(line+"\n");
                activity.setCaretPosition(activity.getDocument().getLength());
            }
            @Override protected void done() {
                try {
                    var result = get();
                    completedOutputRoot = result.state() == GuiI1Execution.State.VERIFIED
                            ? result.outputRoot() : null;
                    if(result.state()==GuiI1Execution.State.VERIFIED)verifiedSelection=attempt;
                    activity.append("Backend status: " + result.state() + "\n" + result.summary());
                    status.setText(result.state().name().replace('_', ' '));
                } catch (Exception uncertain) {
                    completedOutputRoot = null;
                    activity.append("Execution was not confirmed.\nNo verified publication is claimed; preserve the destination for inspection.");
                    status.setText("NOT VERIFIED");
                } finally {
                    setBusy(false);
                    // No deterministic percentage is available in the accepted backends.
                    activityState.setText(status.getText());
                }
            }
        }.execute();
    }
    /** Classic text-field placeholder, no custom colors or modern card styling. */
    private static final class HintField extends JTextField {
        private String hint = "";
        void setHint(String value) { hint = value; repaint(); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getText().isEmpty() && !hint.isEmpty()) {
                graphics.setFont(getFont());
                graphics.setColor(new Color(143, 143, 143));
                FontMetrics metrics = graphics.getFontMetrics();
                graphics.drawString(hint, getInsets().left + 5,
                        (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
            }
        }
    }
    private record TextChange(Runnable action) implements DocumentListener {
        @Override public void insertUpdate(DocumentEvent e) { action.run(); }
        @Override public void removeUpdate(DocumentEvent e) { action.run(); }
        @Override public void changedUpdate(DocumentEvent e) { action.run(); }
    }
}
