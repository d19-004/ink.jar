package inkjar;

import javax.swing.*;
import javax.swing.UIManager;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            String username = promptForUsername();
            if (username == null || username.isBlank()) {
                System.exit(0);
            }
            new Game(username);
        });
    }

    private static String promptForUsername() {
        String defaultName = "Player" + (char)('A' + (int)(Math.random() * 26));
        JTextField field = new JTextField(defaultName);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("Enter your username:"));
        panel.add(field);

        int result = JOptionPane.showConfirmDialog(
                null, panel, "ink.jar", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            String name = field.getText().trim();
            if (!name.isEmpty() && name.length() < 20) return name;
            JOptionPane.showMessageDialog(null, "Username must be 1-19 characters.", "Error", JOptionPane.ERROR_MESSAGE);
        }
        return null;
    }
}
