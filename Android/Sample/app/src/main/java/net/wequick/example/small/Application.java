package net.wequick.example.small;

import net.wequick.small.Small;

/**
 * Created by galen on 15/11/3.
 */
public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();

        try {
            Small.setup(this);
        } catch (Small.SmallSetupException e) {
            e.printStackTrace();
        }

    }
}
