<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.maraog.weather">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:label="Maraog Weather"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault">

        <service
            android:name=".MaraogTileService"
            android:exported="true"
            android:label="Maraog Weather"
            android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">

            <intent-filter>
                <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
            </intent-filter>
        </service>

    </application>
</manifest>
  
