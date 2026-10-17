package com.jepongdevxyz.devxyzdns;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.VpnService;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

/** Quick Settings tile: tap to start/stop DevxyzDNS. */
@RequiresApi(api = Build.VERSION_CODES.N)
public class DnsTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (DnsVpnService.isRunning()) {
            startService(new Intent(this, DnsVpnService.class).setAction(DnsVpnService.ACTION_STOP));
        } else if (Prefs.getProvider(this) == null) {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(i);
            return;
        } else if (VpnService.prepare(this) == null) {
            startService(new Intent(this, DnsVpnService.class));
        } else {
            // VPN consent not granted yet — open the app to complete it.
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(i);
            return;
        }
        updateTile();
    }

    private void updateTile() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = DnsVpnService.isRunning();
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("DevxyzDNS");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            t.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        }
        t.updateTile();
    }
}
