package net.wequick.example.small.app.mine;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.Toast;
import com.example.hellojni.HelloPluginJni;

/**
 * Created by galen on 15/11/11.
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toast.makeText(MainActivity.this, HelloPluginJni.stringFromJNI(), Toast.LENGTH_SHORT).show();
    }
}
