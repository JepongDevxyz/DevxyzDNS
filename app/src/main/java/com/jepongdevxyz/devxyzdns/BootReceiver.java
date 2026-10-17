package com.jepongdevxyz.devxyzdns;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

/** Restart the DNS changer after device boot when the user enabled it. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && Prefs.startOnBoot(context)
                && Prefs.getProvider(context) != null
                && VpnService.prepare(context) == null) {
            context.startService(new Intent(context, DnsVpnService.class));
        }
    }
}
