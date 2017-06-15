/*
 *
 * Copyright (c) 2013 - 2017 Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.password;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class SecurePasswordInputPanel extends Panel {

    public class MyActionListener implements ActionListener {

        @Override
        public void actionPerformed(final ActionEvent event) {
            JButton btn = (JButton) event.getSource();
            String pressedKey = (String) btn.getClientProperty("key");

            if (CAPS.equals(pressedKey)) {
                for (JButton button : buttons) {
                    String text = button.getText();
                    text = caps ? text.toLowerCase() : text.toUpperCase();
                    button.setText(text);
                }
                caps = !caps;
                return;
            }

            if (BACKSPACE.equals(pressedKey)) {
                if (password.length() > 0) {
                    password = password.substring(0, password.length() - 1);
                }
            } else if (CLEAR.equals(pressedKey)) {
                password = "";
            } else {
                password += btn.getText();
            }
            passwordField.setText(password);
        } // method actionPerformed

    } // class MyActionListener

    private static final long serialVersionUID = 1L;

    // CHECKSTYLE:SKIP
    private static final String BACKSPACE = "\u21E6";

    // CHECKSTYLE:SKIP
    private static final String CAPS = "\u21E7";

    private static final String CLEAR = "Clear";

    private static final String OK = "OK";

    private static final Map<Integer, String[]> KEYS_MAP = new HashMap<Integer, String[]>();

    private final JPasswordField passwordField;

    private final Set<JButton> buttons = new HashSet<JButton>();

    private String password = "";
    private boolean caps;

    static {
        int idx = 0;
        KEYS_MAP.put(idx++, new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"});
        KEYS_MAP.put(idx++, new String[]{"!", "@", "§" , "#", "$", "%", "^", "&", "*",
            "(", ")", "{", "}"});
        KEYS_MAP.put(idx++, new String[]{"'", "\"", "=", "_", ":", ";", "?", "~", "|", ",",
            ".", "-", "/"});
        KEYS_MAP.put(idx++, new String[]{"q", "w", "e", "r", "z", "y", "u", "i", "o", "p"});
        KEYS_MAP.put(idx++, new String[]{"a", "s", "d", "f", "g", "h", "j", "k", "j", BACKSPACE});
        KEYS_MAP.put(idx++, new String[] {CAPS, "z", "x", "c", "v", "b", "n", "m", CLEAR});
    }

    private SecurePasswordInputPanel() {
        super(new GridLayout(0, 1));

        this.passwordField = new JPasswordField(10);
        passwordField.setEditable(false);

        add(passwordField);

        Set<Integer> rows = new HashSet<Integer>(KEYS_MAP.keySet());
        final int n = rows.size();

        SecureRandom random = new SecureRandom();
        while (!rows.isEmpty()) {
            int row = random.nextInt() % n;
            if (!rows.contains(row)) {
                continue;
            }

            String[] keys = KEYS_MAP.get(row);
            rows.remove(row);

            JPanel panel = new JPanel();
            for (int column = 0; column < keys.length; column++) {
                String text = keys[column];
                JButton button = new JButton(text);
                button.setFont(button.getFont().deriveFont(Font.TRUETYPE_FONT));
                if (CLEAR.equalsIgnoreCase(text)) {
                    button.setBackground(Color.red);
                } else if (CAPS.equalsIgnoreCase(text) || BACKSPACE.equalsIgnoreCase(text)) {
                    button.setBackground(Color.lightGray);
                } else {
                    buttons.add(button);
                }

                button.putClientProperty("key", text);
                button.addActionListener(new MyActionListener());
                panel.add(button);
            } // end for
            add(panel);
        } // end while(!rows.isEmpty())

        //setVisible(true);
    } // constructor

    public char[] getPassword() {
        return password.toCharArray();
    }

    public static char[] readPassword(final String prompt) {
        LookAndFeel currentLookAndFeel = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) { // CHECKSTYLE:SKIP
        }

        try {
            SecurePasswordInputPanel gui = new SecurePasswordInputPanel();
            String[] options = new String[]{OK};

            String tmpPrompt = prompt;
            if (tmpPrompt == null || tmpPrompt.isEmpty()) {
                tmpPrompt = "Password required";
            }

            int option = JOptionPane.showOptionDialog(null, gui, tmpPrompt,  JOptionPane.NO_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, options, options[0]);

            if (option == 0) { // pressing OK button
                return gui.getPassword();
            } else {
                return null;
            }
        } finally {
            try {
                UIManager.setLookAndFeel(currentLookAndFeel);
            } catch (UnsupportedLookAndFeelException ex) { // CHECKSTYLE:SKIP
            }
        }
    } // method readPassword

}
