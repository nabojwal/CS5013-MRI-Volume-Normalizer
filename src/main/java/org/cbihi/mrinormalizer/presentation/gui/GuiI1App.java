package org.cbihi.mrinormalizer.presentation.gui;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Dedicated GUI entrypoint; no modifications to console commands or conversion engines. */
public final class GuiI1App {
    private GuiI1App() {}
    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("GUI_I1_REQUIRES_DESKTOP");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                FlatLightLaf.setup();
                UIManager.put("Button.arc", 0);
                UIManager.put("Component.arc", 0);
                UIManager.put("TextComponent.arc", 0);
                UIManager.put("ProgressBar.arc", 0);
                UIManager.put("TabbedPane.showTabSeparators", true);
            } catch (RuntimeException ignored) {
                try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                catch (Exception fallbackUnavailable) { /* Swing remains usable */ }
            }
            new GuiI1Window().setVisible(true);
        });
    }
}
