package ohi.andre.consolelauncher.managers.xml.classes;

import android.content.Context;

/**
 * Created by francescoandreuzzi on 06/03/2018.
 */

public interface XMLPrefsElement {
    XMLPrefsList getValues();
    void write(Context c, XMLPrefsSave save, String value);
    String[] delete();
    String path();
}