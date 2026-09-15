package app.touchdrop

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.ServerSocket
import java.net.Socket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.*
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.*
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.Random
import java.util.concurrent.Executors
import android.util.Base64

/** Foreground-only, one verified peer, bounded sequential transfer protocol. */
class MainActivity : Activity() {
    private val service = "app.touchdrop.files.v5"
    private val maxTransferAttempts = 3
    private val radio by lazy { Nearby.getConnectionsClient(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var epoch = 0
    private var state = "IDLE"
    private var sender = false
    private var peer: String? = null
    private var verified = false
    private var touchConfirmed=false
    private var lastActivity = 0L
    private var attempts = 0
    private var authDialog: AlertDialog? = null
    private var picking = false
    private var preparing = false
    private var files = listOf<Photo>()
    private var incoming = listOf<Photo>()
    private var index = 0
    private var offset = 0L
    private var acknowledged = 0L
    private var completed = 0L
    private var batchId = ""
    private var output: FileOutputStream? = null
    private var currentFile: File? = null
    private var gate: TransferGate? = null
    private var nativeId: Long? = null
    private var nativePayload: Payload? = null
    private var transferStarted = 0L
    private lateinit var sand: SandView
    private lateinit var stage: FrameLayout
    private lateinit var stageText: TextView
    @Volatile private var wifiSocket:Socket?=null
    @Volatile private var wifiServer:ServerSocket?=null
    private var lastProgressTime=0L
    private lateinit var status: TextView
    private lateinit var chosen: TextView
    private lateinit var progressText: TextView
    private lateinit var bar: ProgressBar
    private lateinit var devices: LinearLayout
    private lateinit var selectButton: Button
    private lateinit var sendButton: Button
    private lateinit var receiveButton: Button
    private var previews: LinearLayout? = null
    private val gold = Color.rgb(232,192,112)
    private val ink = Color.rgb(242,233,213)
    private data class Photo(val name:String,val mime:String,val size:Long,val sha:String,val file:File?=null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        File(cacheDir,"fichiers").deleteRecursively(); File(cacheDir,"fichiers").mkdirs()
        File(cacheDir,"incoming").deleteRecursively(); File(cacheDir,"incoming").mkdirs()
        buildUi()
        val launchRoot=findViewById<android.view.ViewGroup>(android.R.id.content)
        val home=launchRoot.getChildAt(0)
        home.alpha=0f;home.scaleX=.96f;home.scaleY=.96f
        // Branded MP4 intro: the supplied gold-sand BNET film plays once,
        // then dissolves into the real app instead of showing a static card.
        val intro=FrameLayout(this).apply{
            setBackgroundColor(Color.BLACK)
            contentDescription="TOUCHDROP — BNET golden sand intro"
            elevation=dp(20).toFloat()
        }
        val introVideo=VideoView(this).apply{
            setBackgroundColor(Color.BLACK)
            setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.touchdrop_intro}"))
            setOnPreparedListener{player->player.isLooping=false;player.setVolume(1f,1f);start()}
        }
        intro.addView(introVideo,FrameLayout.LayoutParams(-1,-1))
        launchRoot.addView(intro,android.view.ViewGroup.LayoutParams(-1,-1))
        var introClosed=false
        fun closeIntro(){
            if(introClosed||isDestroyed)return
            introClosed=true
            introVideo.stopPlayback()
            home.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(1400).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            intro.animate().alpha(0f).setDuration(1400).withEndAction{if(!isDestroyed)launchRoot.removeView(intro)}.start()
        }
        introVideo.setOnCompletionListener{closeIntro()}
        introVideo.setOnErrorListener{_,_,_->closeIntro();true}
        // Safety fallback if a device refuses hardware video decoding.
        handler.postDelayed({closeIntro()},15000L)
        handler.post(object:Runnable { override fun run(){
            if(state !in listOf("IDLE","DONE") && SystemClock.elapsedRealtime()-lastActivity > if(state=="SEARCH")120000 else 60000) stop("Délai dépassé. Relancez la recherche.")
            handler.postDelayed(this,1000)
        }})
    }
    private fun dp(n:Int) = (n*resources.displayMetrics.density).toInt()
    private fun background(color:Int,radius:Float=22f) = GradientDrawable().apply { setColor(color); cornerRadius=dp(radius.toInt()).toFloat() }
    private fun label(parent:LinearLayout,text:String,size:Float=16f):TextView = TextView(this).also { it.text=text;it.textSize=size;it.setTextColor(ink);it.setPadding(0,dp(7),0,dp(7));parent.addView(it) }
    private fun button(parent:LinearLayout,title:String,primary:Boolean=false,action:()->Unit):Button = Button(this).also {
        it.text=title;it.isAllCaps=false;it.textSize=17f;it.setTextColor(if(primary)Color.rgb(25,19,11) else gold)
        val surface=if(primary)GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(255,229,170),gold,Color.rgb(185,134,53))) else GradientDrawable().apply{setColor(Color.rgb(30,28,24));setStroke(dp(1),Color.rgb(91,74,43))}
        surface.cornerRadius=dp(18).toFloat()
        it.background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x44FFFFFF),surface,null)
        it.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);it.elevation=if(primary)dp(4).toFloat() else 0f
        parent.addView(it,LinearLayout.LayoutParams(-1,dp(54)).apply { topMargin=dp(10) });it.setOnClickListener { action() }
    }
    private fun buildUi(){
        val base=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(24,23,20),Color.rgb(8,10,13),Color.rgb(22,18,13)))}
        val shell=FrameLayout(this);shell.addView(base);setContentView(shell)
        base.setOnApplyWindowInsetsListener{v,i->
            if(Build.VERSION.SDK_INT>=30){val bars=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom)}
            else v.setPadding(0,i.systemWindowInsetTop,0,i.systemWindowInsetBottom)
            i
        };base.requestApplyInsets()
        val scroll=ScrollView(this).apply{isVerticalScrollBarEnabled=false};base.addView(scroll)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(20),dp(22),dp(30))};scroll.addView(root)
        val mast=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};root.addView(mast)
        mast.addView(ImageView(this).apply{setImageResource(R.drawable.icon)},LinearLayout.LayoutParams(dp(56),dp(56)).apply{rightMargin=dp(14)})
        val titles=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};mast.addView(titles)
        label(titles,"TOUCHDROP",26f).apply{typeface=Typeface.create("sans-serif-black",Typeface.BOLD);letterSpacing=.06f;setTextColor(gold)}
        label(titles,"GOLD EDITION  /  BNET COMPANY",10f).apply{letterSpacing=.12f;setTextColor(Color.rgb(179,165,137))}
        label(root,"Le partage,\nen un geste.",34f).apply{typeface=Typeface.create("sans-serif-light",Typeface.NORMAL);setPadding(0,dp(24),0,dp(8))}
        label(root,"Vos originaux. Tout près. En toute simplicité.",14f).setTextColor(Color.rgb(181,174,158))
        fun card(title:String):LinearLayout{
            val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL
                background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(39,34,25),Color.rgb(19,20,23))).apply{cornerRadius=dp(24).toFloat();setStroke(dp(1),Color.rgb(83,67,40))}
                setPadding(dp(18),dp(14),dp(18),dp(20))}
            root.addView(panel,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(20)})
            label(panel,title,12f).apply{setTextColor(gold);letterSpacing=.12f}
            return panel
        }
        val selection=card("01  /  VOTRE SÉLECTION")
        chosen=label(selection,"Que souhaitez-vous partager ?",19f)
        val horizontal=HorizontalScrollView(this);previews=LinearLayout(this);horizontal.addView(previews);selection.addView(horizontal)
        selectButton=button(selection,"＋  Ajouter des fichiers"){choose()}
        label(selection,"PHOTOS  ·  VIDÉOS  ·  AUDIO  ·  DOCUMENTS",10f).setTextColor(Color.rgb(179,165,137))
        sendButton=button(selection,"Envoyer  ↗",true){begin(true)}
        receiveButton=button(root,"↓  Recevoir des fichiers"){begin(false)}
        val connection=card("02  /  CONNEXION DE PROXIMITÉ")
        status=label(connection,"Reliez les téléphones au même Wi-Fi ou au point d’accès de l’autre, puis ouvrez Recevoir.",14f)
        devices=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};connection.addView(devices)
        button(connection,"Configurer le Wi-Fi"){startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))}
        label(connection,"Confirmation par contact NFC ou comparaison du code.",12f).setTextColor(Color.rgb(179,165,137))
        val tracking=card("03  /  TRANSFERT")
        bar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100
            progressTintList=android.content.res.ColorStateList.valueOf(gold)
            progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(65,54,34))}
        tracking.addView(bar,LinearLayout.LayoutParams(-1,dp(8)))
        progressText=label(tracking,"Prêt à partager",15f)
        button(tracking,"Arrêter / déconnecter"){stop("Transfert arrêté. Les fichiers terminés restent dans Téléchargements/TouchDrop.")}
        label(root,"50 fichiers · 1 Gio par fichier · 2 Gio par envoi",12f).setTextColor(Color.rgb(179,165,137))
        button(root,"À propos et confidentialité"){
            AlertDialog.Builder(this).setTitle("TOUCHDROP · Gold Edition")
                .setMessage("Transfert Wi-Fi local chiffré. Les deux applications restent ouvertes. Confirmez le code ou le contact NFC.\n\nFichiers reçus : Téléchargements/TouchDrop. Aucun fichier n’est ouvert automatiquement. Les copies temporaires sont supprimées après arrêt ou au lancement suivant.\n\nAucun serveur TOUCHDROP ne reçoit vos fichiers. La découverte utilise Google Play Services et ses traitements propres.\n\nVersion 0.8 de test — LABED ABDNOUR.")
                .setPositiveButton("Fermer",null).show()
        }
        root.addView(BnetBrandView(this),LinearLayout.LayoutParams(-1,dp(42)).apply{topMargin=dp(24)})
        label(root,"BNET COMPANY\nENGINEERING BY LABED ABDNOUR\nTOUCHDROP 0.8",10f).apply{gravity=Gravity.CENTER;letterSpacing=.09f;setTextColor(Color.rgb(166,143,101));setPadding(0,dp(24),0,0)}
        stage=FrameLayout(this).apply{visibility=View.GONE;setBackgroundColor(Color.BLACK)}
        shell.addView(stage,FrameLayout.LayoutParams(-1,-1))
        sand=SandView(this);stage.addView(sand,FrameLayout.LayoutParams(-1,-1))
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(10),dp(20),dp(28))}
        stage.addView(panel,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        stageText=label(panel,"Wi-Fi · préparation",20f)
        button(panel,"Fermer / arrêter"){if(state=="DONE")stage.visibility=View.GONE else stop("Transfert arrêté.")}
        controls()
    }
    private fun touch(){lastActivity=SystemClock.elapsedRealtime()}
    private fun controls(){val idle=state in listOf("IDLE","DONE")&&!preparing;selectButton.isEnabled=idle;sendButton.isEnabled=idle&&files.isNotEmpty();receiveButton.isEnabled=idle;listOf(selectButton,sendButton,receiveButton).forEach{it.alpha=if(it.isEnabled)1f else .4f}}
    private fun choose(){
        if(state !in listOf("IDLE","DONE"))return
        picking=true
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true),10)
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);picking=false
        if(requestCode!=10||resultCode!=RESULT_OK||data==null)return
        val uris=mutableListOf<Uri>();val clip=data.clipData
        if(clip!=null) for(i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) else data.data?.let { uris.add(it) }
        val unique=uris.distinct();if(unique.size>Limits.MAX_FILES){status.text="Sélectionnez au maximum 50 fichiers.";return}
        files.forEach { it.file?.delete() };files=emptyList();preparing=true;controls();chosen.text="Préparation des originaux…";val generation=epoch
        worker.execute {
            val prepared=mutableListOf<Photo>()
            try {
                var total=0L
                unique.forEach { uri ->
                    var name="photo"
                    contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) name=it.getString(0)?:"photo" }
                    val f=File(File(cacheDir,"fichiers"),UUID.randomUUID().toString())
                    try {
                        val hash=MessageDigest.getInstance("SHA-256");var size=0L
                        contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { out ->
                            val b=ByteArray(1024*1024);while(true){require(epoch==generation){"Préparation annulée"};val n=input.read(b);if(n<0)break;size+=n;require(size<=Limits.MAX_FILE&&total+size<=Limits.MAX_BATCH){"Limite : 1 Gio par fichier et 2 Gio par lot."};out.write(b,0,n);hash.update(b,0,n)}
                        }}
                        require(Limits.validSize(size)){"Fichier vide."}
                        val mime=contentResolver.getType(uri)?.takeIf{Limits.validMime(it)}?:"application/octet-stream";total+=size
                        prepared.add(Photo(Limits.cleanName(name),mime,size,hex(hash.digest()),f))
                    } catch(e:Exception){f.delete();throw e}
                }
                runOnUiThread {
                    if(epoch!=generation||isDestroyed){prepared.forEach{it.file?.delete()};return@runOnUiThread}
                    files=prepared;preparing=false;state="IDLE";chosen.text="${files.size} fichiers · ${mb(total)} Mo";controls();status.text="Prêt. Touchez Envoyer, puis choisissez le téléphone trouvé.";previews?.removeAllViews()
                    files.take(6).forEach { photo ->
                        val view=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP };previews?.addView(view,LinearLayout.LayoutParams(dp(56),dp(56)).apply {rightMargin=dp(6)})
                        worker.execute { val dim=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(photo.file!!.path,dim);var step=1;while(maxOf(dim.outWidth,dim.outHeight)/step>256)step*=2;val options=BitmapFactory.Options().apply{inSampleSize=step};val bitmap=BitmapFactory.decodeFile(photo.file.path,options)?:fileCard(photo.name,photo.mime);runOnUiThread { if(epoch==generation&&!isDestroyed)view.setImageBitmap(bitmap) } }
                    }
                }
            } catch(e:Exception){prepared.forEach{it.file?.delete()};runOnUiThread{if(epoch==generation){preparing=false;chosen.text="Aucune image préparée";status.text="Sélection refusée : ${e.message}";controls()}}}
        }
    }
    private fun checkImage(f:File):String {
        val header=ByteArray(16);f.inputStream().use{it.read(header)}
        val mime=Limits.signature(header)?:throw IOException("Format non pris en charge. Utilisez JPEG, PNG, WebP ou GIF.")
        val o=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(f.path,o)
        require(o.outWidth>0&&o.outHeight>0&&o.outWidth.toLong()*o.outHeight<=200_000_000){"Dimensions invalides ou image trop grande."}
        return mime
    }
    private fun permissions():Array<String> {
        val p=mutableListOf<String>();if(Build.VERSION.SDK_INT<=32)p.addAll(listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        if(Build.VERSION.SDK_INT>=31)p.addAll(listOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_ADVERTISE,Manifest.permission.BLUETOOTH_CONNECT))
        if(Build.VERSION.SDK_INT>=33)p.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        return p.filter {checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}.toTypedArray()
    }
    private fun begin(sending:Boolean){
        if(preparing||state !in listOf("IDLE","DONE"))return
        if(sending&&files.isEmpty()){status.text="Choisissez d’abord des fichiers.";return}
        sender=sending
        val p=permissions();if(p.isNotEmpty()){requestPermissions(p,11);return}
        if(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)!=ConnectionResult.SUCCESS){status.text="Services Google Play absents ou à mettre à jour. Nearby n’est pas disponible.";return}
        state="SEARCH";touch();attempts=0;devices.removeAllViews();controls();window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status.text=if(sender)"Recherche des téléphones en mode Recevoir…" else "Visible à proximité : ${Build.MODEL}. Gardez cet écran ouvert."
        val generation=epoch
        try {
            val task=if(sender) radio.startDiscovery(service,discovery,DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
                else radio.startAdvertising(Build.MODEL.take(40),service,lifecycle,AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
            task.addOnFailureListener{if(epoch==generation)stop("Recherche impossible. Vérifiez Wi-Fi, Bluetooth et les permissions. ${it.localizedMessage}")}
        } catch(e:Exception){stop("Recherche impossible : ${e.message}")}
    }
    override fun onRequestPermissionsResult(code:Int,p:Array<out String>,r:IntArray){super.onRequestPermissionsResult(code,p,r)
        if(code==11){if(r.isNotEmpty()&&r.all{it==PackageManager.PERMISSION_GRANTED})begin(sender) else status.text="Permissions refusées. Le transfert de proximité reste désactivé."}
    }
    private val discovery=object:EndpointDiscoveryCallback(){
        override fun onEndpointFound(id:String,info:DiscoveredEndpointInfo){if(state!="SEARCH"||!sender)return
            if(devices.findViewWithTag<Button>(id)!=null)return
            button(devices,"Connecter · ${info.endpointName.take(40)}") {
                if(state!="SEARCH")return@button
                peer=id;state="CONNECTING";touch();devices.removeAllViews();radio.stopDiscovery()
                val generation=epoch
                radio.requestConnection(Build.MODEL.take(40),id,lifecycle).addOnFailureListener{if(epoch==generation)stop("Connexion impossible : ${it.localizedMessage}")}
            }.tag=id
        }
        override fun onEndpointLost(id:String){devices.findViewWithTag<Button>(id)?.let{devices.removeView(it)}}
    }
    private val lifecycle=object:ConnectionLifecycleCallback(){
        override fun onConnectionInitiated(id:String,info:ConnectionInfo){
            if((sender&&(state!="CONNECTING"||peer!=id))||(!sender&&(state!="SEARCH"||peer!=null))||++attempts>5||info.authenticationDigits.isBlank()) {radio.rejectConnection(id);return}
            peer=id;state="AUTH";touch();radio.stopAdvertising();val generation=epoch
            touchConfirmed=false;armNfc(info,generation)
            authDialog=AlertDialog.Builder(this@MainActivity).setTitle("📱  CONTACT NFC")
                .setMessage("${info.endpointName.take(40)}\n\n1. Gardez les deux écrans déverrouillés.\n2. Rapprochez les faces arrière des deux smartphones (côté caméras).\n3. Maintenez-les dos contre dos pendant 1 à 2 secondes.\n\nLe téléphone vibrera et affichera « Contact NFC confirmé ».\n\nSi le contact NFC ne fonctionne pas, vérifiez ce code :\n${info.authenticationDigits}")
                .setPositiveButton("Confirmer par le code"){_,_->
                    if(epoch==generation&&state=="AUTH"){state="AUTH_WAIT";touch();radio.acceptConnection(id,payloads).addOnFailureListener{if(epoch==generation)stop("Confirmation échouée.")};status.text="Attente de la confirmation de l’autre téléphone…"}
                }.setNegativeButton("Refuser"){_,_->radio.rejectConnection(id);stop("Connexion refusée.")}
                .setOnCancelListener{radio.rejectConnection(id);stop("Connexion annulée.")}.show()
            handler.postDelayed({if(epoch==generation&&state in listOf("AUTH","AUTH_WAIT")){radio.rejectConnection(id);stop("Code expiré après 30 secondes.")}},30000)
        }
        override fun onConnectionResult(id:String,result:ConnectionResolution){
            if(id!=peer||state!="AUTH_WAIT")return
            if(!result.status.isSuccess){stop("Connexion refusée ou interrompue.");return}
            clearNfc();verified=true;state="CONNECTED";touch();status.text=if(touchConfirmed)"Contact NFC confirmé · connexion vérifiée." else "Connexion vérifiée par code."
            if(touchConfirmed)Toast.makeText(this@MainActivity,"Contact NFC confirmé",Toast.LENGTH_SHORT).show()
            if(sender){status.text=if(touchConfirmed)"Contact NFC confirmé · proposition du lot…" else "Connexion vérifiée · proposition du lot…";batchId=UUID.randomUUID().toString();index=0;completed=0;state="OFFER_WAIT";val arr=JSONArray();files.forEach{arr.put(JSONObject().put("name",it.name).put("mime",it.mime).put("size",it.size).put("sha",it.sha))};send(JSONObject().put("t","offer").put("batch",batchId).put("files",arr))}
        }
        override fun onDisconnected(id:String){if(id==peer)stop(if(state=="DONE")"Transfert terminé. Téléphone déconnecté." else "Liaison coupée. Les fichiers déjà sauvegardées restent dans la galerie ; recommencez les autres.")}
    }
    private fun clearNfc(){
        TouchSession.clear()
        runCatching{NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)}
    }
    private fun armNfc(info:ConnectionInfo,generation:Int){
        val adapter=NfcAdapter.getDefaultAdapter(this)?:return
        if(!adapter.isEnabled)return
        val raw=info.rawAuthenticationToken?:return
        if(raw.isEmpty())return
        val token=MessageDigest.getInstance("SHA-256").digest(raw)
        val confirm={
            runOnUiThread{
                if(epoch==generation&&state=="AUTH"){
                    touchConfirmed=true;state="AUTH_WAIT";authDialog?.dismiss();touch()
                    radio.acceptConnection(peer!!,payloads).addOnFailureListener{if(epoch==generation)stop("Contact confirmé mais connexion échouée.")}
                    status.text="Contact NFC confirmé · connexion…"
                    Toast.makeText(this@MainActivity,"Contact NFC confirmé",Toast.LENGTH_SHORT).show()
                }
            }
        }
        if(!sender){
            TouchSession.token=token;TouchSession.expires=SystemClock.elapsedRealtime()+30000;TouchSession.confirm=confirm
        }else runCatching{
            adapter.enableReaderMode(this,{tag->
                val iso=IsoDep.get(tag)
                if(iso!=null)try{
                    iso.connect();iso.timeout=2500
                    val response=iso.transceive(TouchService.SELECT)
                    if(response.size==34&&response.takeLast(2).toByteArray().contentEquals(TouchService.OK)&&MessageDigest.isEqual(token,response.copyOfRange(0,32))){
                        val ack=iso.transceive(byteArrayOf(0x80.toByte(),0x10,0,0,32)+token)
                        if(ack.contentEquals(TouchService.OK))confirm()
                    }
                }catch(_:Exception){}finally{runCatching{iso.close()}}
            },NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,null)
        }
    }
    private fun send(j:JSONObject){
        val id=peer?:return;val generation=epoch
        if(!verified)return
        val bytes=j.toString().toByteArray(Charsets.UTF_8)
        if(bytes.size>32768){stop("Message trop volumineux.");return}
        radio.sendPayload(id,Payload.fromBytes(bytes)).addOnFailureListener {if(epoch==generation)stop("Envoi interrompu : ${it.localizedMessage}")}
    }
    private val payloads=object:PayloadCallback(){
        override fun onPayloadReceived(id:String,payload:Payload){
            if(id!=peer||!verified)return
            if(payload.type!=Payload.Type.BYTES){radio.cancelPayload(payload.id);stop("Type non autorisé.");return}
            try {val bytes=payload.asBytes()?:throw IOException("Message vide");require(bytes.size<=32768);message(JSONObject(String(bytes,Charsets.UTF_8)))}
            catch(e:Exception){stop("Transfert refusé : ${e.message?.take(100)}")}
        }
        override fun onPayloadTransferUpdate(id:String,u:PayloadTransferUpdate){}
    }
    private fun base(t:String)=JSONObject().put("t",t).put("batch",batchId).put("i",index)
    private fun message(j:JSONObject){
        val type=j.getString("t")
        if(type=="offer"){
            require(!sender&&state=="CONNECTED");batchId=j.getString("batch");require(batchId.length<=40)
            val arr=j.getJSONArray("files");require(arr.length() in 1..Limits.MAX_FILES)
            val list=mutableListOf<Photo>();var total=0L
            for(i in 0 until arr.length()){val p=arr.getJSONObject(i);val size=p.getLong("size");val mime=p.getString("mime");val sha=p.getString("sha");require(Limits.validSize(size)&&Limits.validMime(mime)&&sha.matches(Regex("[0-9a-f]{64}")));total+=size;require(total<=Limits.MAX_BATCH);list.add(Photo(Limits.cleanName(p.getString("name")),mime,size,sha))}
            require(filesDir.usableSpace>total+list.maxOf{it.size}+20*1024*1024){"Stockage insuffisant"};incoming=list;state="OFFER_CONFIRM";touch();val generation=epoch
            if(touchConfirmed){index=0;completed=0;openIncoming();state="RX";transferStarted=SystemClock.elapsedRealtime();sand.outgoing=false;touch();send(base("ready"));status.text="Contact NFC confirmé · réception automatique";return}
            authDialog=AlertDialog.Builder(this).setTitle("Recevoir ${list.size} fichiers ?").setMessage("${mb(total)} Mo · originaux\n\n${list.take(5).joinToString("\n"){it.name}}\n\nDestination : Download/TouchDrop")
                .setPositiveButton("Recevoir"){_,_->if(epoch==generation&&state=="OFFER_CONFIRM"){try{index=0;completed=0;openIncoming();state="RX";transferStarted=SystemClock.elapsedRealtime();sand.outgoing=false;touch();send(base("ready"));status.text="Réception en cours…"}catch(e:Exception){stop("Impossible de préparer la réception : ${e.message}")}}}
                .setNegativeButton("Refuser"){_,_->stop("Lot refusé.")}.setOnCancelListener{stop("Lot annulé.")}.show();return
        }
        require(j.getString("batch")==batchId)
        when(type){
            "ready" -> {require(sender&&state=="OFFER_WAIT"&&j.getInt("i")==0);transferStarted=SystemClock.elapsedRealtime();sand.outgoing=true;sendWifiMetadata()}
            "file" -> {
                require(!sender&&state=="RX"&&j.getInt("i")==index)
                nativeId=j.getLong("payload");nativePayload=null
                val preview=j.optString("preview")
                if(preview.isNotEmpty()){
                    require(preview.length<=24000);val b=Base64.decode(preview,Base64.NO_WRAP)
                    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(b,0,b.size,bounds)
                    require(bounds.outWidth in 1..256&&bounds.outHeight in 1..256)
                    sand.photo=BitmapFactory.decodeByteArray(b,0,b.size)
                }
                sand.amount=0f;state="RX_FILE";touch();receiveWifi()
            }
            "fileReady" -> {
                require(sender&&state=="FILE_READY"&&j.getInt("i")==index&&j.getLong("payload")==nativeId)
                state="FILE_WAIT";touch();sendWifi(j)
            }
            "saved" -> {
                require(sender&&state=="FILE_WAIT"&&j.getInt("i")==index)
                nativeId=null;nativePayload=null;completed+=files[index].size;index++;touch()
                if(index==files.size){send(base("done"));finishBatch()}else sendWifiMetadata()
            }
            "done" -> {require(!sender&&state=="RX_DONE"&&j.getInt("i")==incoming.size);finishBatch()}
            else -> throw IOException("Message inattendu")
        }
    }
    private fun sendWifiMetadata(){
        val photo=files[index];val generation=epoch;state="PREVIEW"
        worker.execute{try{
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(photo.file!!.path,bounds)
            val options=BitmapFactory.Options().apply{inSampleSize=maxOf(1,maxOf(bounds.outWidth,bounds.outHeight)/512)}
            val bitmap=BitmapFactory.decodeFile(photo.file.path,options)?:fileCard(photo.name,photo.mime)
            val thumb=bitmap?.let{Bitmap.createScaledBitmap(it,maxOf(1,256*it.width/maxOf(it.width,it.height)),maxOf(1,256*it.height/maxOf(it.width,it.height)),true)}
            val bytes=ByteArrayOutputStream();var quality=80;do{bytes.reset();thumb?.compress(Bitmap.CompressFormat.JPEG,quality,bytes);quality-=15}while(bytes.size()>18000&&quality>=20)
            val preview=if(bytes.size()<=18000)Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP) else ""
            val payloadId=SecureRandom().nextLong()
            runOnUiThread{if(epoch==generation&&state=="PREVIEW"){
                nativeId=payloadId;nativePayload=null;sand.photo=bitmap;sand.amount=0f
                state="FILE_READY";touch();send(base("file").put("payload",payloadId).put("preview",preview))
            }}
        }catch(e:Exception){runOnUiThread{if(epoch==generation)stop("Préparation impossible : ${e.message}")}}}
    }
    private fun fileCard(name:String,mime:String):Bitmap{
        val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val isVideo=mime.startsWith("video/");val isAudio=mime.startsWith("audio/");val isPdf=mime=="application/pdf"
        val accent=when{isVideo->Color.rgb(126,170,255);isAudio->Color.rgb(219,142,255);isPdf->Color.rgb(255,182,92);else->Color.rgb(247,207,126)}
        val deep=when{isVideo->Color.rgb(18,32,68);isAudio->Color.rgb(42,20,58);isPdf->Color.rgb(62,31,16);else->Color.rgb(35,29,18)}
        val card=RectF(14f,14f,498f,498f)
        canvas.drawRoundRect(card,38f,38f,Paint(Paint.ANTI_ALIAS_FLAG).apply{
            shader=LinearGradient(14f,14f,498f,498f,deep,Color.rgb(12,14,22),Shader.TileMode.CLAMP)
        })
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=Typeface.create("sans-serif-black",Typeface.BOLD)}
        val dust=Random(name.hashCode().toLong())
        repeat(180){
            p.style=Paint.Style.FILL;p.color=accent;p.alpha=30+dust.nextInt(100)
            canvas.drawCircle(24f+dust.nextFloat()*464f,24f+dust.nextFloat()*464f,.5f+dust.nextFloat()*2.8f,p)
        }
        p.style=Paint.Style.STROKE;p.strokeWidth=3f;p.color=accent;p.alpha=190
        canvas.drawRoundRect(RectF(28f,28f,484f,484f),30f,30f,p)
        p.style=Paint.Style.FILL;p.alpha=255;p.textSize=34f;p.color=accent
        val type=when{isVideo->"VIDÉO";isAudio->"AUDIO";isPdf->"PDF";else->"FICHIER"}
        canvas.drawText(type,44f,92f,p)
        if(isVideo){
            p.color=Color.argb(220,255,255,255);canvas.drawCircle(256f,220f,72f,p)
            p.color=deep
            canvas.drawPath(Path().apply{moveTo(238f,180f);lineTo(238f,260f);lineTo(300f,220f);close()},p)
            p.color=accent;p.alpha=120;p.style=Paint.Style.STROKE;p.strokeWidth=5f
            canvas.drawCircle(256f,220f,86f,p)
        }else if(isPdf){
            p.color=Color.argb(225,255,255,255);canvas.drawRoundRect(RectF(202f,145f,310f,292f),12f,12f,p)
            p.color=accent;p.style=Paint.Style.FILL
            canvas.drawPath(Path().apply{moveTo(274f,145f);lineTo(310f,181f);lineTo(274f,181f);close()},p)
            p.color=deep;p.textSize=28f;canvas.drawText("PDF",214f,246f,p)
        }else{
            p.color=Color.argb(225,255,255,255);canvas.drawRoundRect(RectF(206f,145f,306f,292f),12f,12f,p)
            p.color=accent;p.style=Paint.Style.STROKE;p.strokeWidth=7f
            canvas.drawLine(226f,190f,286f,190f,p);canvas.drawLine(226f,222f,286f,222f,p);canvas.drawLine(226f,254f,270f,254f,p)
        }
        p.style=Paint.Style.FILL;p.color=Color.WHITE;p.alpha=245;p.textSize=21f
        name.take(84).chunked(27).take(3).forEachIndexed{i,line->canvas.drawText(line,44f,350f+i*29,p)}
        p.color=accent;p.alpha=220;p.textSize=16f
        canvas.drawText(if(isVideo)"APERÇU VIDÉO  ·  ORIGINAL" else "ORIGINAL  ·  PRÊT À TRANSFÉRER",44f,462f,p)
        return bitmap
    }
    private fun wifiAddresses():List<String>{
        val cm=getSystemService(ConnectivityManager::class.java)
        val result=mutableListOf<String>()
        cm.allNetworks.forEach{n->
            if(cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true)
                cm.getLinkProperties(n)?.linkAddresses?.forEach{if(it.address is Inet4Address)result.add(it.address.hostAddress!!)}
        }
        // A phone hosting a hotspot may not expose the AP as a ConnectivityManager Network.
        NetworkInterface.getNetworkInterfaces()?.toList()?.filter{it.isUp&&(it.name.startsWith("wlan")||it.name.startsWith("ap")||it.name.startsWith("swlan"))}?.forEach{iface->
            iface.inetAddresses.toList().filterIsInstance<Inet4Address>().filter{!it.isLoopbackAddress}.forEach{result.add(it.hostAddress!!)}
        }
        return result.distinct().take(6)
    }
    private fun wireProgress(generation:Int,done:Long,size:Long,total:Long){
        val now=SystemClock.elapsedRealtime()
        if(now-lastProgressTime<80&&done<size)return
        lastProgressTime=now
        runOnUiThread{if(epoch==generation){
            touch();sand.amount=(done.toFloat()/size).coerceAtMost(.98f);showProgress(completed+done,total)
        }}
    }
    private fun receiveWifi(){
        val addresses=wifiAddresses()
        if(addresses.isEmpty()){stop("Aucun Wi-Fi local. Connectez les deux téléphones au même Wi-Fi, ou au point d’accès de l’autre.");return}
        val server=ServerSocket(0).apply{soTimeout=20000};wifiServer=server
        val key=ByteArray(32).also{SecureRandom().nextBytes(it)}
        val generation=epoch;val photo=incoming[index];val target=currentFile!!;val context="$batchId:$index"
        val total=incoming.sumOf{it.size}
        stage.visibility=View.VISIBLE;stageText.text="WI-FI LOCAL · attente de la liaison…"
        send(base("fileReady").put("payload",nativeId).put("hosts",JSONArray(addresses)).put("port",server.localPort).put("key",Base64.encodeToString(key,Base64.NO_WRAP)))
        worker.execute{
            var received=false
            var lastError:Exception?=null
            for(attempt in 1..maxTransferAttempts){
                if(epoch!=generation)break
                try{
                    runOnUiThread{if(epoch==generation&&attempt>1)stageText.text="WI-FI LOCAL · nouvelle tentative $attempt/$maxTransferAttempts…"}
                    server.accept().use{socket->
                        wifiSocket=socket;socket.soTimeout=20000;socket.receiveBufferSize=1024*1024
                        // Every attempt starts from a clean temporary file. The
                        // authenticated stream is all-or-nothing, so a partial
                        // file can never be published to the gallery.
                        target.outputStream().buffered(1024*1024).use{out->
                            FastWire.receive(socket.getInputStream(),out,photo.size,key,context,photo.sha){done->wireProgress(generation,done,photo.size,total)}
                        }
                    }
                    received=true
                    break
                }catch(e:Exception){
                    lastError=e
                    target.delete()
                    if(attempt<maxTransferAttempts){
                        runOnUiThread{if(epoch==generation)status.text="Liaison interrompue · reprise automatique $attempt/$maxTransferAttempts…"}
                        Thread.sleep(250)
                    }
                }
            }
            try{
                if(received)runOnUiThread{if(epoch==generation&&state=="RX_FILE"){state="SAVING";touch();persistIncoming()}else target.delete()}
                else if(epoch==generation)runOnUiThread{stop("Wi-Fi interrompu après $maxTransferAttempts tentatives : ${lastError?.message}. Vérifiez le même réseau et l’isolation des appareils.")}
            }finally{runCatching{server.close()};key.fill(0)}
        }
    }
    private fun sendWifi(j:JSONObject){
        val arr=j.getJSONArray("hosts");require(arr.length() in 1..6)
        val hosts=(0 until arr.length()).map{arr.getString(it)}
        require(hosts.all{it.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))})
        val port=j.getInt("port");require(port in 1024..65535)
        val key=Base64.decode(j.getString("key"),Base64.NO_WRAP);require(key.size==32)
        val cm=getSystemService(ConnectivityManager::class.java)
        val network=cm.allNetworks.firstOrNull{cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true}
        if(network==null){stop("Connectez ce téléphone au Wi-Fi du destinataire ou à son point d’accès. Les fichiers ne passent pas par Bluetooth.");return}
        val generation=epoch;val photo=files[index];val context="$batchId:$index";val total=files.sumOf{it.size}
        stage.visibility=View.VISIBLE;stageText.text="WI-FI LOCAL · connexion…"
        worker.execute{
            var sent=false
            var lastError:Exception?=null
            for(attempt in 1..maxTransferAttempts){
                if(epoch!=generation)break
                try{
                    runOnUiThread{if(epoch==generation&&attempt>1)stageText.text="WI-FI LOCAL · reprise $attempt/$maxTransferAttempts…"}
                    var connected:Socket?=null
                    for(host in hosts){
                        require(epoch==generation){"Transfert annulé"}
                        val socket=network.socketFactory.createSocket();wifiSocket=socket
                        try{socket.connect(java.net.InetSocketAddress(host,port),2500);connected=socket;break}
                        catch(e:Exception){socket.close()}
                    }
                    val socket=connected?:throw IOException("destinataire inaccessible sur ce Wi-Fi")
                    socket.use{
                        it.sendBufferSize=1024*1024;it.soTimeout=20000
                        photo.file!!.inputStream().buffered(1024*1024).use{input->
                            FastWire.transfer(input,it.getOutputStream(),photo.size,key,context){done->wireProgress(generation,done,photo.size,total)}
                        }
                        it.shutdownOutput()
                    }
                    sent=true
                    break
                }catch(e:Exception){
                    lastError=e
                    if(attempt<maxTransferAttempts){
                        runOnUiThread{if(epoch==generation)status.text="Envoi interrompu · reprise automatique $attempt/$maxTransferAttempts…"}
                        Thread.sleep(250)
                    }
                }
            }
            try{
                if(!sent&&epoch==generation)runOnUiThread{stop("Envoi Wi-Fi impossible après $maxTransferAttempts tentatives : ${lastError?.message}")}
            }finally{key.fill(0)}
        }
    }
    private fun openIncoming(){
        offset=0;currentFile=File(File(cacheDir,"incoming"),UUID.randomUUID().toString())
    }
    private fun persistIncoming(){
        val photo=incoming[index];val file=currentFile!!;val generation=epoch
        worker.execute {var uri:Uri?=null;var committed=false
            try {
                require(file.length()==photo.size){"Taille incohérente"}
                val dimensions=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,dimensions)
                var sample=1;while(maxOf(dimensions.outWidth,dimensions.outHeight)/sample>1024)sample*=2
                val opts=BitmapFactory.Options().apply{inSampleSize=sample};val receivedPreview=BitmapFactory.decodeFile(file.path,opts)?:fileCard(photo.name,photo.mime)
                
                val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,"TD_${UUID.randomUUID().toString().take(6)}_${photo.name}");put(MediaStore.Downloads.MIME_TYPE,photo.mime);put(MediaStore.Downloads.RELATIVE_PATH,"Download/TouchDrop");put(MediaStore.Downloads.IS_PENDING,1)}
                uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:throw IOException("Galerie inaccessible")
                contentResolver.openOutputStream(uri!!)!!.use {out->file.inputStream().use{input->
                    val block=ByteArray(1024*1024);var tick=0L
                    while(true){require(epoch==generation){"Enregistrement annulé"};val n=input.read(block);if(n<0)break;out.write(block,0,n)
                        val now=SystemClock.elapsedRealtime()
                        if(now-tick>500){tick=now;runOnUiThread{if(epoch==generation){touch();stageText.text="Enregistrement du fichier vérifié…"}}}
                    }
                }}
                // Publish only on the main thread after checking this transfer's generation.
                val savedUri=uri!!
                runOnUiThread {
                    if(epoch!=generation||state!="SAVING"){contentResolver.delete(savedUri,null,null);return@runOnUiThread}
                    var published=false
                    try {
                        val changed=contentResolver.update(savedUri,ContentValues().apply{put(MediaStore.Downloads.IS_PENDING,0)},null,null);require(changed==1)
                        published=true;sand.photo=receivedPreview;sand.amount=1f;completed+=photo.size;val receipt=base("saved");index++;currentFile=null
                        if(index==incoming.size)state="RX_DONE" else{openIncoming();state="RX"}
                        touch();send(receipt);status.text="$index / ${incoming.size} fichiers enregistrées et vérifiées."
                    }catch(e:Exception){if(!published)contentResolver.delete(savedUri,null,null);stop("Sauvegarde interrompue. Les fichiers déjà publiées restent dans la galerie.")}
                }
                committed=true
            } catch(e:Exception){if(uri!=null)runCatching{contentResolver.delete(uri!!,null,null)};runOnUiThread{if(epoch==generation)stop("Fichier non enregistré : ${e.message}")}}
            finally{file.delete();if(!committed&&uri!=null)runCatching{contentResolver.delete(uri!!,null,null)}}
        }
    }
    private fun finishBatch(){
        state="DONE";stageText.text="100 % · ORIGINAL VÉRIFIÉ";sand.amount=1f;bar.progress=100;progressText.text="100 % · fichiers vérifiés";status.text=if(sender)"Transfert terminé : les fichiers sont enregistrées sur l’autre téléphone." else "Réception terminée. Retrouvez vos fichiers dans Download/TouchDrop."
        files.forEach{it.file?.delete()};files=emptyList();chosen.text="Sélection terminée";previews?.removeAllViews();window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Disconnect after the final receipt can leave the local transport queue.
        val generation=epoch;handler.postDelayed({if(epoch==generation&&state=="DONE"){radio.stopAllEndpoints();peer=null;verified=false;controls()}},1500)
    }
    private fun showProgress(done:Long,total:Long){
        val seconds=((SystemClock.elapsedRealtime()-transferStarted)/1000.0).coerceAtLeast(.1)
bar.progress=if(total>0)((done*100/total).toInt()).coerceAtMost(99) else 0;progressText.text="${bar.progress} % · ${mb(done)} / ${mb(total)} Mo · ${String.format(java.util.Locale.FRANCE,"%.1f",done/1048576.0/seconds)} Mo/s";stageText.text="WI-FI LOCAL · "+progressText.text}
    /** Stop the active transport without discarding a prepared outgoing batch.
     * A Wi‑Fi/Nearby disconnect is a transport event, not a new file selection:
     * keeping the staged originals lets the user reconnect and retry safely.
     */
    private fun stop(message:String){
        runCatching{wifiSocket?.close()};runCatching{wifiServer?.close()};wifiSocket=null;wifiServer=null
        stage.visibility=View.GONE
        clearNfc();touchConfirmed=false
        nativeId?.let{runCatching{radio.cancelPayload(it)}}
        nativePayload?.asFile()?.asUri()?.let{if(!sender)runCatching{contentResolver.delete(it,null,null)}}
        nativeId=null;nativePayload=null;sand.amount=0f
        epoch++;state="IDLE";verified=false;peer=null;preparing=false;authDialog?.dismiss();authDialog=null
        runCatching{radio.stopAdvertising();radio.stopDiscovery();radio.stopAllEndpoints()};runCatching{output?.close()};output=null;currentFile?.delete();currentFile=null
        // Keep the prepared outgoing files and their previews. They are deleted
        // only after a successful batch (finishBatch) or when the user chooses
        // a new selection. This prevents a brief disconnect from losing work.
        incoming=emptyList();devices.removeAllViews()
        chosen.text=if(files.isEmpty())"Aucune image sélectionnée" else "${files.size} fichiers prêts · sélection conservée"
        status.text="$message  Vous pouvez relancer l’envoi sans re-sélectionner."
        progressText.text=if(bar.progress==100)"100 % · terminé" else "Transfert arrêté · fichiers conservés"
        controls();window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    // Android may call onStop while a Nearby permission/authentication window
    // is being shown. Do not tear down the transport from that lifecycle hook;
    // explicit Stop, timeout, disconnect, or destruction still performs cleanup.
    override fun onStop(){super.onStop()}
    override fun onDestroy(){runCatching{wifiSocket?.close()};runCatching{wifiServer?.close()};clearNfc();epoch++;handler.removeCallbacksAndMessages(null);runCatching{radio.stopAllEndpoints();radio.stopAdvertising();radio.stopDiscovery();output?.close()};files.forEach{it.file?.delete()};currentFile?.delete();worker.shutdown();super.onDestroy()}
    private fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it)}
    private fun mb(n:Long)=String.format(java.util.Locale.FRANCE,"%.1f",n/1048576.0)
}
