// Copyright MyScript. All rights reserved.

package com.myscript.iink.demo;

import android.app.Application;
import android.content.Context;

import com.myscript.certificate.MyCertificate;
import com.myscript.iink.Configuration;
import com.myscript.iink.Engine;

public class IInkApplication extends Application
{
  private static Engine engine;
  private static Context context;

  @Override
  public void onCreate() {
    super.onCreate();
    context = this;
  }

  public static synchronized Engine getEngine()
  {
    if (engine == null)
    {
      engine = Engine.create(MyCertificate.getBytes());
    }

    String configDir = "zip://" + context.getPackageCodePath() + "!/assets/conf";
    Configuration config = engine.getConfiguration();
    config.setStringArray("configuration-manager.search-path", new String[]{configDir});
    config.setString("lang", "zh_CN");
    config.setBoolean("gesture.enable", false);

    return engine;
  }

}
