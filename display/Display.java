package display;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Display extends JFrame {// 同步控制台窗口
    private static final Color BACKGROUND = new Color(24, 28, 36);
    private static final Color PANEL = new Color(34, 40, 51);
    private static final Color TEXT = new Color(235, 239, 244);
    private static final Color MUTED = new Color(157, 169, 184);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Map<String, NodeState> nodes = new LinkedHashMap<>();
    private final DefaultTableModel eventModel = new DefaultTableModel(
            new Object[] { "时间", "节点", "事件", "状态", "迭代" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel iterationLabel = new JLabel();
    private final JLabel syncLabel = new JLabel();
    private final JLabel eventCountLabel = new JLabel();
    private final JLabel feedbackLabel = new JLabel();
    private final JPanel nodePanel = new JPanel(new GridLayout(0, 1, 0, 8));
    private final JTextArea declaration = new JTextArea();
    private final Timer simulationTimer;
    private int iteration = 1;
    private int eventCount;
    private int simulationStep;

    public Display() {
        super("METstudio · 项目同步显示");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 650));
        setSize(1180, 760);
        setLocationRelativeTo(null);
        initialiseNodes();
        simulationTimer = new Timer(1800, event -> simulateNextEvent());
        buildInterface();
        refreshView();
    }

    private void initialiseNodes() { // 初始化节点状态
        nodes.put("训练设计", new NodeState("待机", "定义本轮练习目标"));
        nodes.put("序列编排", new NodeState("待机", "生成可播放序列"));
        nodes.put("音频执行", new NodeState("待机", "播放音频并报告时钟"));
        nodes.put("训练反馈", new NodeState("待机", "收集答案并评分"));
        nodes.put("验证同步", new NodeState("待机", "检查事件顺序与快照"));
    }

    private void buildInterface() { // 构建界面
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildMainContent(), BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel buildHeader() {// 构建头部面板
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);

        JLabel title = new JLabel("METstudio / 同步控制台");
        title.setForeground(TEXT);
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        header.add(title, BorderLayout.WEST);

        JPanel metrics = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        metrics.setOpaque(false);
        styleMetric(iterationLabel);
        styleMetric(syncLabel);
        styleMetric(eventCountLabel);
        metrics.add(iterationLabel);
        metrics.add(syncLabel);
        metrics.add(eventCountLabel);
        header.add(metrics, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton start = button("开始同步");
        start.addActionListener(event -> simulationTimer.start());
        JButton pause = button("暂停");
        pause.addActionListener(event -> simulationTimer.stop());
        JButton next = button("推进一事件");
        next.addActionListener(event -> simulateNextEvent());
        JButton export = button("导出快照");
        export.addActionListener(event -> exportSnapshot());
        actions.add(start);
        actions.add(pause);
        actions.add(next);
        actions.add(export);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel buildMainContent() {// 构建主内容面板
        JPanel left = new JPanel(new BorderLayout(0, 14));
        left.setOpaque(false);
        left.add(sectionTitle("节点状态 / NODE STATUS"), BorderLayout.NORTH);
        left.add(nodePanel, BorderLayout.CENTER);
        left.add(buildDeclaration(), BorderLayout.SOUTH);

        JTable eventTable = new JTable(eventModel);
        eventTable.setRowHeight(28);
        eventTable.setBackground(PANEL);
        eventTable.setForeground(TEXT);
        eventTable.setGridColor(new Color(55, 64, 78));
        eventTable.getTableHeader().setBackground(new Color(45, 53, 66));
        eventTable.getTableHeader().setForeground(MUTED);
        eventTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        JScrollPane events = new JScrollPane(eventTable);
        events.setBorder(BorderFactory.createLineBorder(new Color(55, 64, 78)));

        JPanel right = new JPanel(new BorderLayout(0, 14));
        right.setOpaque(false);
        right.add(sectionTitle("事件时间线 / EVENT STREAM"), BorderLayout.NORTH);
        right.add(events, BorderLayout.CENTER);
        right.add(feedbackLabel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.38);
        split.setDividerSize(8);
        split.setBorder(null);
        return panelWith(split);
    }

    private JPanel buildDeclaration() {
        declaration.setEditable(false);
        declaration.setLineWrap(true);
        declaration.setWrapStyleWord(true);
        declaration.setRows(7);
        declaration.setText("同步声明\n" +
                "每条事件必须携带 iterationId、revision、nodeId、type 和 correlationId。\n" +
                "旧版本拒绝应用，缺失版本标记为 DESYNC；恢复连接后按事件重放。\n" +
                "当前为本地演示事件源，接入 Spring Boot 时替换 simulateNextEvent()。\n");
        declaration.setBackground(PANEL);
        declaration.setForeground(MUTED);
        declaration.setFont(new Font("SansSerif", Font.PLAIN, 12));
        declaration.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(55, 64, 78)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(declaration, BorderLayout.CENTER);
        return wrapper;
    }

    private void simulateNextEvent() {
        String[] nodeNames = { "训练设计", "序列编排", "音频执行", "训练反馈", "验证同步" };
        String[] statuses = { "完成", "运行中", "运行中", "等待作答", "已验证" };
        String[] eventTypes = { "GOAL_CREATED", "SEQUENCE_READY", "PLAY_STARTED", "ANSWER_RECEIVED",
                "SNAPSHOT_VERIFIED" };
        int index = simulationStep % nodeNames.length;
        NodeState state = nodes.get(nodeNames[index]);
        state.status = statuses[index];
        state.detail = eventTypes[index] + " · revision " + (eventCount + 1);
        appendEvent(nodeNames[index], eventTypes[index], statuses[index]);
        simulationStep++;
        if (simulationStep % nodeNames.length == 0) {
            iteration++;
            feedbackLabel.setText("  已完成一轮闭环，准备进入迭代 " + iteration);
        }
        refreshView();
    }

    private void appendEvent(String node, String type, String status) {
        eventCount++;
        eventModel.insertRow(0, new Object[] {
                LocalDateTime.now().format(CLOCK), node, type, status, "ITER-" + iteration });
        while (eventModel.getRowCount() > 80) {
            eventModel.removeRow(eventModel.getRowCount() - 1);
        }
    }

    private void refreshView() {
        iterationLabel.setText("ITER-" + iteration);
        syncLabel.setText("● 本地已同步");
        syncLabel.setForeground(new Color(109, 211, 157));
        eventCountLabel.setText("事件 " + eventCount);
        nodePanel.removeAll();
        for (Map.Entry<String, NodeState> entry : nodes.entrySet()) {
            nodePanel.add(nodeCard(entry.getKey(), entry.getValue()));
        }
        nodePanel.revalidate();
        nodePanel.repaint();
    }

    private JPanel nodeCard(String name, NodeState state) {
        JPanel card = new JPanel(new BorderLayout(10, 4));
        card.setBackground(PANEL);
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JLabel nameLabel = new JLabel(name);
        nameLabel.setForeground(TEXT);
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        JLabel detail = new JLabel(state.detail);
        detail.setForeground(MUTED);
        detail.setFont(new Font("SansSerif", Font.PLAIN, 11));
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(nameLabel);
        text.add(detail);
        JLabel status = new JLabel(state.status, SwingConstants.RIGHT);
        status.setForeground(statusColor(state.status));
        status.setFont(new Font("SansSerif", Font.BOLD, 12));
        card.add(text, BorderLayout.CENTER);
        card.add(status, BorderLayout.EAST);
        return card;
    }

    private Color statusColor(String status) {
        if ("完成".equals(status) || "已验证".equals(status))
            return new Color(109, 211, 157);
        if ("运行中".equals(status) || "等待作答".equals(status))
            return new Color(244, 194, 92);
        return MUTED;
    }

    private void exportSnapshot() {
        Path output = Paths.get("display", "snapshot.json");
        List<String> lines = new ArrayList<>();
        lines.add("{");
        lines.add("  \"projectId\": \"METstudio\",");
        lines.add("  \"iterationId\": \"ITER-" + iteration + "\",");
        lines.add("  \"revision\": " + eventCount + ",");
        lines.add("  \"syncStatus\": \"SYNCED\",");
        lines.add("  \"nodes\": {");
        int index = 0;
        for (Map.Entry<String, NodeState> entry : nodes.entrySet()) {
            String suffix = index++ == nodes.size() - 1 ? "" : ",";
            lines.add("    \"" + entry.getKey() + "\": \"" + entry.getValue().status + "\"" + suffix);
        }
        lines.add("  }");
        lines.add("}");
        try {
            Files.createDirectories(output.getParent());
            Files.write(output, lines, StandardCharsets.UTF_8);
            feedbackLabel.setText("  快照已导出：" + output);
        } catch (IOException error) {
            feedbackLabel.setText("  快照导出失败：" + error.getMessage());
        }
    }

    private JPanel panelWith(java.awt.Component component) {// 包装组件为面板
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private JLabel sectionTitle(String text) { // 构建分区标题标签
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        return label;
    }

    private void styleMetric(JLabel label) {
        label.setForeground(TEXT);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
    }

    private JButton button(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setForeground(TEXT);
        button.setBackground(new Color(54, 65, 81));
        button.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        return button;
    }

    public static void main(String[] args) {// 程序入口
        SwingUtilities.invokeLater(() -> new Display().setVisible(true));
    }

    private static class NodeState {
        private String status;
        private String detail;

        private NodeState(String status, String detail) {
            this.status = status;
            this.detail = detail;
        }
    }
}