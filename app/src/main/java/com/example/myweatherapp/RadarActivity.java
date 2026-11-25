package com.example.myweatherapp;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class RadarActivity extends AppCompatActivity {

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radar);

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(5, 13, 25));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        double latitude = getIntent().getDoubleExtra("latitude", 53.9);
        double longitude = getIntent().getDoubleExtra("longitude", 27.56);
        String city = getIntent().getStringExtra("city");
        if (city == null || city.trim().isEmpty()) city = "Текущее место";

        TextView title = findViewById(R.id.radarTitle);
        TextView status = findViewById(R.id.radarStatus);
        ImageButton back = findViewById(R.id.radarBack);
        WebView radar = findViewById(R.id.radarWebView);
        title.setText("Радар · " + city);
        back.setOnClickListener(v -> finish());

        radar.setBackgroundColor(Color.rgb(6, 17, 31));
        radar.getSettings().setJavaScriptEnabled(true);
        radar.getSettings().setDomStorageEnabled(true);
        radar.getSettings().setBuiltInZoomControls(false);
        radar.getSettings().setDisplayZoomControls(false);
        radar.setWebChromeClient(new WebChromeClient());
        radar.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                status.setVisibility(View.GONE);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                   WebResourceError error) {
                if (request.isForMainFrame()) {
                    status.setText("Не удалось открыть радар. Проверьте интернет.");
                    status.setVisibility(View.VISIBLE);
                }
            }
        });
        radar.loadDataWithBaseURL("https://www.rainviewer.com/", buildRadarHtml(latitude, longitude),
                "text/html", "UTF-8", null);
    }

    private String buildRadarHtml(double latitude, double longitude) {
        String lat = String.format(Locale.US, "%.6f", latitude);
        String lon = String.format(Locale.US, "%.6f", longitude);
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"
                + "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>"
                + "<style>html,body,#map{height:100%;margin:0;background:#07111f}"
                + ".leaflet-control-zoom a{background:rgba(18,36,58,.82)!important;color:#fff!important;border-color:rgba(255,255,255,.15)!important}"
                + ".leaflet-control-attribution{background:rgba(5,14,27,.72)!important;color:#cfe8ff!important}"
                + ".leaflet-control-attribution a{color:#8fd8ff!important}"
                + "#panel{position:absolute;z-index:999;left:12px;right:12px;bottom:30px;padding:12px 14px;border:1px solid rgba(255,255,255,.27);border-radius:18px;background:rgba(9,25,43,.72);backdrop-filter:blur(18px);color:#fff;font:14px sans-serif;display:flex;gap:12px;align-items:center}"
                + "#play{border:0;border-radius:14px;background:#7bd8ff;color:#062036;font-weight:700;padding:10px 14px}#time{flex:1}"
                + "</style></head><body><div id='map'></div><div id='panel'><button id='play'>▶</button><span id='time'>Загрузка радара…</span></div>"
                + "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>"
                + "const map=L.map('map',{zoomControl:true}).setView([" + lat + "," + lon + "],7);"
                + "L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'© OpenStreetMap · Radar © RainViewer'}).addTo(map);"
                + "L.circleMarker([" + lat + "," + lon + "],{radius:7,color:'#fff',weight:2,fillColor:'#35b7ff',fillOpacity:1}).addTo(map);"
                + "let layers=[],frames=[],index=0,timer=null;const label=document.getElementById('time'),play=document.getElementById('play');"
                + "function show(i){layers.forEach(x=>map.removeLayer(x));index=i;const f=frames[i];const url=window.rv.host+f.path+'/256/{z}/{x}/{y}/2/1_1.png';"
                + "const layer=L.tileLayer(url,{opacity:.72,zIndex:20,maxZoom:7});layer.addTo(map);layers=[layer];label.textContent='Осадки · '+new Date(f.time*1000).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'});}"
                + "function stop(){if(timer){clearInterval(timer);timer=null;play.textContent='▶';}}"
                + "play.onclick=()=>{if(timer){stop();return;}play.textContent='Ⅱ';timer=setInterval(()=>show((index+1)%frames.length),850);};"
                + "fetch('https://api.rainviewer.com/public/weather-maps.json').then(r=>r.json()).then(d=>{window.rv=d;frames=d.radar.past||[];if(!frames.length)throw Error();show(frames.length-1);}).catch(()=>{label.textContent='Радар временно недоступен';play.style.display='none'});"
                + "</script></body></html>";
    }

    @Override
    protected void onDestroy() {
        WebView radar = findViewById(R.id.radarWebView);
        if (radar != null) {
            radar.stopLoading();
            radar.destroy();
        }
        super.onDestroy();
    }
}
