package ohi.andre.consolelauncher.managers.xml.options;

import android.os.Build;

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;

/**
 * Created by francescoandreuzzi on 24/09/2017.
 */

public enum Ui implements XMLPrefsSave {

    show_enter_button {
        @Override
        public String defaultValue() {
            return "true";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }

        @Override
        public String info() {
            return "Hide/show the enter button";
        }
    },
    system_font {
        @Override
        public String defaultValue() {
            return "false";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }

        @Override
        public String info() {
            return "If false, the default t-ui font (\"Lucida Console\") will be used for all texts";
        }
    },
    ram_size {
        @Override
        public String defaultValue() {
            return "13";
        }

        @Override
        public String type() {
            return XMLPrefsSave.INTEGER;
        }

        @Override
        public String info() {
            return "The ram label font size";
        }
    },
    battery_size {
        @Override
        public String defaultValue() {
            return "13";
        }

        @Override
        public String type() {
            return XMLPrefsSave.INTEGER;
        }

        @Override
        public String info() {
            return "The battery label font size";
        }
    },
    device_size {
        @Override
        public String defaultValue() {
            return "13";
        }

        @Override
        public String type() {
            return XMLPrefsSave.INTEGER;
        }

        @Override
        public String info() {
            return "The device label font size";
        }
    },
    time_size {
        @Override
        public String defaultValue() {
            return "13";
        }

        @Override
        public String type() {
            return XMLPrefsSave.INTEGER;
        }

        @Override
        public String info() {
            return "The time label font size";
        }
    },
    storage_size {
        @Override
        public String defaultValue() {
            return "13";
        }

        @Override
        public String type() {
            return XMLPrefsSave.INTEGER;
        }

        @Override
        public String info() {
            return "The storage label font size";
        }
    },
    network_size {
        @Override
        public String defaultValue() {
            return "13";
        }

        @Override
        public String type() {
            return XMLPrefsSave.INTEGER;
        }

        @Override
        public String info() {
            return "The network label font size";
        }
    },
    input_output_size {
        @Override
        public String defaultValue() {
            return "15";
        }

        @Override
        public String type() {
            return XMLPrefsSave.INTEGER;
        }

        @Override
        public String info() {
            return "The input/output font size";
        }
    },
    input_bottom {
        @Override
        public String defaultValue() {
            return "true";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }

        @Override
        public String info() {
            return "If false, the input field will be placed on top of the screen";
        }
    },
    show_device_name {
        @Override
        public String defaultValue() {
            return "true";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }

        @Override
        public String info() {
            return "If false, the device label will be hidden";
        }
    },
    username {
        @Override
        public String defaultValue() {
            return "user";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }

        @Override
        public String info() {
            return "Your username";
        }
    },
    deviceName {
        @Override
        public String defaultValue() {
            return Build.DEVICE;
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }

        @Override
        public String info() {
            return "Your device name";
        }
    },
    system_wallpaper {
        @Override
        public String defaultValue() {
            return "false";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }

        @Override
        public String info() {
            return "If true, your system wallpaper will be used as background";
        }
    },
    fullscreen {
        @Override
        public String defaultValue() {
            return "false";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }

        @Override
        public String info() {
            return "If true, t-ui will run in fullscreen mode";
        }
    },
    input_prefix {
        @Override
        public String defaultValue() {
            return "$";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }

        @Override
        public String info() {
            return "The prefix placed before every input";
        }
    },
    input_root_prefix {
        @Override
        public String defaultValue() {
            return "#";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }

        @Override
        public String info() {
            return "The prefix placed before a root command (\"su ...\")";
        }
    },
    display_margin_mm {
        @Override
        public String defaultValue() {
            return "0,0,0,0";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }

        @Override
        public String info() {
            return "[left margin],[top margin],[right margin],[bottom margin]";
        }
    },
    ignore_bar_color {
        @Override
        public String defaultValue() {
            return "false";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }

        @Override
        public String info() {
            return "If true, statusbar_color and navigationbar_color will be ignored";
        }
    },
    show_app_installed {
        @Override
        public String defaultValue() {
            return "true";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }

        @Override
        public String info() {
            return "If true, you will receive a message when you install an app";
        }
    },
    show_app_uninstalled {
        @Override
        public String defaultValue() {
            return "true";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }

        @Override
        public String info() {
            return "If true, you will receive a message when you uninstall an app";
        }
    },
    show_session_info {
        @Override
        public String defaultValue() {
            return "true";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }

        @Override
        public String info() {
            return "If true, when your input field is empty there will be a short line containing some information about the current session";
        }
    },
    show_restart_message {
        @Override
        public String defaultValue() {
            return "true";
        }

        @Override
        public String info() {
            return "If false, the restart message won\'t be shown";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }
    },
    show_weather {
        @Override
        public String defaultValue() {
            return "true";
        }

        @Override
        public String info() {
            return "If true, you will see a label containing the weather in your area";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }
    },
    weather_size {
        @Override
        public String defaultValue() {
            return "13";
        }

        @Override
        public String type() {
            return XMLPrefsSave.INTEGER;
        }

        @Override
        public String info() {
            return "Weather size";
        }
    },
    unlock_size {
        @Override
        public String defaultValue() {
            return "13";
        }

        @Override
        public String type() {
            return XMLPrefsSave.INTEGER;
        }

        @Override
        public String info() {
            return "Unlock size";
        }
    },
    statusbar_light_icons {
        @Override
        public String defaultValue() {
            return "true";
        }

        @Override
        public String type() {
            return XMLPrefsSave.BOOLEAN;
        }

        @Override
        public String info() {
            return "If true, your status bar icons will be white. Dark otherwise";
        }
    },
    bgrect_params {
        @Override
        public String defaultValue() {
            return "2,0";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }

        @Override
        public String info() {
            return "[Stroke width],[Rect corner radius]";
        }
    },
    shadow_params {
        @Override
        public String defaultValue() {
            return "2,2,0.2";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }

        @Override
        public String info() {
            return "[Shadow X offset],[Shadow Y offset],[Shadow radius]";
        }
    },
    output_field_margins {
        @Override
        public String defaultValue() {
            return "3,3,0,0";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }

        @Override
        public String info() {
            return "[horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]";
        }
    },
    input_area_margins {
        @Override
        public String defaultValue() {
            return "3,3,0,0";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }

        @Override
        public String info() {
            return "The dimension of the input area (prefix + input field + toolbar + suggestions). [horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]";
        }
    },
    toolbar_margins {
        @Override
        public String defaultValue() {
            return "3,3,0,0";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }

        @Override
        public String info() {
            return "[horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]";
        }
    },
    suggestions_area_margin {
        @Override
        public String defaultValue() {
            return "3,3,0,0";
        }

        @Override
        public String type() {
            return XMLPrefsSave.TEXT;
        }

        @Override
        public String info() {
            return "[horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]";
        }
    };

    @Override
    public XMLPrefsElement parent() {
        return XMLPrefsManager.XMLPrefsRoot.UI;
    }

    @Override
    public String label() {
        return name();
    }

    @Override
    public String[] invalidValues() {
        return null;
    }

    @Override
    public String getLowercaseString() {
        return label();
    }

    @Override
    public String getString() {
        return label();
    }
}