import app.melotrail.video.adapter.*
import app.melotrail.video.application.*
import app.melotrail.video.domain.*
import kotlinx.serialization.json.*
import java.nio.file.*
import java.security.MessageDigest
import java.time.Instant

private val json=Json { encodeDefaults=true; prettyPrint=true }
private fun sha(p:Path)=Files.newInputStream(p).use { f -> val d=MessageDigest.getInstance("SHA-256");val b=ByteArray(1048576);while(true){val n=f.read(b);if(n<0)break;d.update(b,0,n)};d.digest().joinToString(""){"%02x".format(it)} }
private fun pin(id:String,p:Path)=VideoGenerationDependencyPin(id,sha(p),p.toRealPath().toString())
fun main(args:Array<String>){
 val root=Path.of(args[0]).toRealPath();val repo=Path.of(args[1]).toRealPath();val mode=args[2]
 val run=root.resolve("render");val projectRoot=run.resolve("project")
 val protected=listOf(repo.resolve("data"),repo.resolve("docs/pictures/video/tabi-assets"))
 val store=VideoProjectStore(protected)
 if(mode=="prepare"){
  Files.createDirectory(run)
  val lifecycle=VideoProjectLifecycle(store,idFactory={"tokyo-scenery-join"})
  var session=(lifecycle.create(CreateVideoProject(projectRoot,"Tokyo main-window join proof","tokyo-scenery","vg4-05")) as VideoProjectLifecycleResult.Opened).session
  val images=VideoImageFiles();val assets=repo.resolve("docs/pictures/video/tabi-assets/scenario");val derived=assets.resolve("scenery-20261002T100234Z")
  fun import(id:String,file:Path,role:VideoReferenceRole):VideoAssetImportResult.Imported {
   val r=VideoAssetImport(lifecycle,images,idFactory={id}).import(session,ImportVideoAsset(file,role));check(r is VideoAssetImportResult.Imported){r.toString()};session=r.session;return r
  }
  val finished=import("neutral",derived.resolve("neutral-comfy-1080p.png"),VideoReferenceRole.COMPLETE_SCENE)
  val foreground=import("fixed-cabin",derived.resolve("fixed-neutral-foreground.png"),VideoReferenceRole.ENVIRONMENT)
  val mask=import("cabin-mask",derived.resolve("fixed-neutral-occlusion.png"),VideoReferenceRole.ENVIRONMENT)
  val inputs=linkedMapOf("far-old" to assets.resolve("tokyo-parallax-far-5600x1080.png"),"far-new" to derived.resolve("far-extended-5600x1080.png"),"middle" to assets.resolve("tokyo-parallax-middle-5600x1080.png"),"near-old" to assets.resolve("tokyo-parallax-near-5600x1080.png"),"near-new" to assets.resolve("tokyo-clockfront-near-v5/tokyo-near-section-v5-candidate-6200x1080.png"))
  val scenery=inputs.mapValues{(id,p)->import(id,p,VideoReferenceRole.ENVIRONMENT)};val roles=inputs.keys.toList()
  val viewport=VideoRect("scene",0.0,0.0,1920.0,1080.0);val sceneId=VideoVersionedId("tokyo-main-window",1)
  val request=PrepareVideoAnimationAssets(sceneId,finished.asset.id,
   scenery=scenery.map{(id,a)->val b=VideoRect("scene",0.0,0.0,a.asset.original.width.toDouble(),1080.0);VideoSceneryAnimationAsset(VideoPlacedAnimationAsset(id,a.asset.id,b),b,"$id-coverage")},
   foregroundLayers=listOf(VideoPlacedAnimationAsset("cabin",foreground.asset.id,viewport)),
   masks=listOf(VideoMaskAnimationAsset(VideoPlacedAnimationAsset("cabin-mask",mask.asset.id,viewport),listOf("cabin")+roles,VideoMaskPurpose.OCCLUSION)),
   depthRelations=listOf(VideoDepthRelation("near-old","middle"),VideoDepthRelation("near-new","middle"),VideoDepthRelation("middle","far-old"),VideoDepthRelation("middle","far-new"))+roles.map{VideoDepthRelation("cabin",it)},
   occlusionRelations=roles.map{VideoOcclusionRelation("cabin",it,"cabin-mask")})
  val scenes=VideoPreparedSceneStore(store,images);val imported=VideoPreparedSceneImport(store,scenes,images).import(projectRoot,session.project.revision,request);check(imported is VideoPreparedSceneImportResult.Saved){imported.toString()}
  val scene=scenes.load(projectRoot,sceneId);val project=store.open(projectRoot);val record=project.preparedSceneVersions.single{it.id==scene.id}
  val preparedPins=(listOf(record.artifact)+record.consumedArtifacts).distinct().mapIndexed{i,a->VideoGenerationDependencyPin("prepared-$i",a.sha256,store.resolveArtifact(projectRoot,a).toString())}
  val tools=Path.of(System.getProperty("user.home"),"Library/Application Support/MelotrailVideo/tools/ffmpeg/9.0.1-melotrail-1/bin").toRealPath();val canvas=repo.resolve("tools/video-motion/node_modules/@napi-rs/canvas")
  val canvasFiles=listOf("index.js","js-binding.js","geometry.js","load-image.js").map{canvas.resolve(it)}+listOf(canvas.parent.resolve("canvas-darwin-arm64/skia.darwin-arm64.node"))
  val runtime=VideoControlledMotionRuntimeBinding(pin("node",Path.of("/opt/homebrew/Cellar/node/25.8.2/bin/node")),pin("compositor",repo.resolve("tools/video-motion/render.cjs")),pin("scenery",repo.resolve("tools/video-motion/scenery.cjs")),pin("canvas-manifest",canvas.resolve("package.json")),canvasFiles.mapIndexed{i,p->pin("canvas-artifact-$i",p)},pin("ffmpeg",tools.resolve("ffmpeg")),pin("ffprobe",tools.resolve("ffprobe")),pin("media-manifest",tools.resolve("melotrail-video-tools.json")),"0.1.80")
  val descriptor=buildJsonObject{
   put("schema",CONTROLLED_MOTION_DESCRIPTOR_SCHEMA);put("preparedScene",json.encodeToJsonElement(VideoPreparedScene.serializer(),scene));put("seed",2026100205);put("fps",30)
   put("canvas",buildJsonObject{put("coordinateSpaceId","scene");put("width",1920);put("height",1080)})
   put("frameRange",buildJsonObject{put("startFrame",1170);put("frameCount",300)});put("controls",buildJsonArray{})
   put("scenery",buildJsonObject{
    put("schema","melotrail-rigid-scenery-v1");put("mode","moving");put("viewport",buildJsonObject{put("coordinateSpaceId","scene");put("x",0);put("y",0);put("width",1920);put("height",1080)})
    put("camera",buildJsonObject{put("startFrame",1170);put("durationFrames",300);put("travelXPixels",480.0*299/599);put("travelYPixels",0);put("motionBlurSamples",3);put("shutterFraction",0.5)})
    put("planes",buildJsonArray{for(role in listOf("far","middle","near"))add(buildJsonObject{put("id",role);put("sections",buildJsonArray{
     fun section(id:String,start:Int,end:Int)=buildJsonObject{put("coverageId","$id-coverage");put("worldX",-1170.0*480/599*(listOf("far","middle","near").indexOf(role)+1));put("worldY",0);put("startFrame",start);put("endFrameExclusive",end)}
     if(role=="middle")add(section(role,1170,1470))else{add(section("$role-old",1170,1230));add(section("$role-new",1230,1470))}
    })})})
   })
  }.toString()
  val reference=scene.source.references.single{it.id==finished.asset.id}
  val identity="${project.id}:${project.revision}:${reference.id.id}:${reference.id.version}:${reference.descriptorArtifact.sha256}:${reference.original.artifact.sha256}:${record.id.id}:${record.id.version}:${record.artifact.sha256}:"+scene.consumedArtifacts().sortedBy{it.relativePath}.joinToString("|"){"${it.relativePath}:${it.sha256}"}
  val fingerprint=MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString(""){"%02x".format(it)}
  val execution=VideoLocalExecutionPolicy(900000,2L*1024*1024*1024,8L*1024*1024*1024)
  val motion=VideoControlledMotionRequest(preparedPins,1170,1470,2026100205,VideoControlledMotionDescriptor(project.id,fingerprint,descriptor,runtime))
  val helpers=listOf(pin("host",root.resolve("scripts/SceneryPilot.kt")),pin("prep",root.resolve("scripts/prepare-scenery.cjs")),pin("preparation",root.resolve("checks/preparation.json")),pin("admission",root.resolve("admission.json")))
  val input=VideoControlledMotionGenerationInput("Ten-second main-window scenery study at absolute 39..49s, preserving original 480/599 pixels/frame and rigid three-plane depth. Static corrected neutral only.",preparedPins+runtime.allPins+helpers,motion,media=VideoControlledMediaBinding(execution,2L*1024*1024*1024,128L*1024*1024,20L*1024*1024*1024,1,1,"image2-h264-yuv420p-silent-square-v1"),primaryPrompt="Quiet Tokyo travel with a seamless offscreen handoff and clock-front district arrival.")
  val job=VideoGenerationJobRequest("tokyo-scenery-join",project.id,VideoControlledMediaStage.BACKEND_ID,emptyList(),input,controlledMotionRequestFingerprint(VideoControlledMediaStage.BACKEND_ID,input,emptyList(),1),1,Instant.now().toString(),execution)
  Files.writeString(run.resolve("job-request.json"),json.encodeToString(VideoGenerationJobRequest.serializer(),job),StandardOpenOption.CREATE_NEW);Files.writeString(run.resolve("motion-request.json"),descriptor,StandardOpenOption.CREATE_NEW)
  println("PREPARED: production import and typed request; no native video run.");return
 }
 require(mode=="render");Files.writeString(run.resolve("render-once.txt"),Instant.now().toString(),StandardOpenOption.CREATE_NEW)
 val request=json.decodeFromString(VideoGenerationJobRequest.serializer(),Files.readString(run.resolve("job-request.json")));val input=request.input as VideoControlledMotionGenerationInput
 input.dependencyPins.forEach{check(sha(Path.of(it.ownedPath!!))==it.sha256)}
 val output=Files.createDirectory(projectRoot.resolve("controlled-output"));val jobs=VideoJobStore(run.resolve("jobs"),request.projectId,protected)
 val coordinator=VideoJobCoordinator(request.projectId,jobs,listOf(VideoControlledMediaStage(jobs,request.projectId,projectRoot,output)))
 var attempt:String?=null;val hook=Thread{attempt?.let{runCatching{coordinator.cancel(request.id,it)}}};Runtime.getRuntime().addShutdownHook(hook)
 val start=System.nanoTime()
 try{
  var result=coordinator.submit(request);check(result is VideoJobResult.Accepted){result.toString()};attempt=result.attempt!!.id
  while(true){
   check((System.nanoTime()-start)/1e9<900){"Shared runner deadline"};Thread.sleep(1000)
   result=coordinator.reconcile(request.id,attempt!!);check(result is VideoJobResult.Accepted){result.toString()}
   if(result.attempt!!.status.isTerminal){check(result.attempt!!.status==VideoGenerationAttemptStatus.SUCCEEDED){result.attempt.toString()};break}
  }
  val artifact=(result as VideoJobResult.Accepted).job.outputs.single();val media=output.resolve(artifact.relativePath!!);check(sha(media)==artifact.sha256)
  Files.copy(media,root.resolve("review/tokyo-window-join-10s-1080p.mp4"))
  Files.writeString(root.resolve("checks/native-result.json"),buildJsonObject{put("movieSha256",sha(media));put("requestFingerprint",request.requestFingerprint);put("frames",300);put("fps",30);put("seconds",10);put("absoluteStartFrame",1170);put("joinFrame",1230);put("elapsedSeconds",(System.nanoTime()-start)/1e9);put("humanReview","PENDING");put("productionControlledStage",true);put("characterActionsCombined",false)}.toString(),StandardOpenOption.CREATE_NEW)
  println("SCENERY_COMPLETED "+media)
 }finally{attempt?.let{val job=coordinator.snapshot().jobs.single();if(!job.attempts.single().status.isTerminal)coordinator.cancel(request.id,it)};Runtime.getRuntime().removeShutdownHook(hook)}
}
