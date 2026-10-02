import app.melotrail.video.ComfyVideoHostProbe;
import app.melotrail.video.ValidatedRequest;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

/** Exact 97-frame evidence admission. The old approximately-five-second test probe
 * deliberately rejects 3.88s; production runtime/job/backend policies are unchanged. */
public final class NarrowBreathAdmission {
    static void require(boolean ok,String why) { if(!ok)throw new IllegalArgumentException(why); }
    static Path existing(String value, boolean directory) throws Exception {
        Path p=Path.of(value);
        require(p.isAbsolute()&&p.normalize().equals(p)&&p.toRealPath().equals(p),"Noncanonical or linked path");
        require(directory?Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS):Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS),"Missing input");
        return p;
    }
    static void owned(Path p) throws Exception {
        var mode=Files.getPosixFilePermissions(p);
        require(!mode.contains(PosixFilePermission.GROUP_WRITE)&&!mode.contains(PosixFilePermission.OTHERS_WRITE),"Unsafe write permissions");
    }
    static ValidatedRequest validate(Path raw) throws Exception {
        var path=existing(raw.toAbsolutePath().normalize().toString(),false);var parent=path.getParent();owned(path);owned(parent);
        var q=ComfyVideoHostProbe.INSTANCE.decodeRequest$melotrail_test(Files.readString(path));
        require(q.getSchemaVersion()==1&&q.getRunId().matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}"),"Invalid schema or ID");
        var output=Path.of(q.getOutputDirectory());
        require(output.isAbsolute()&&output.normalize().equals(output)&&parent.equals(output.getParent())&&!Files.exists(output,LinkOption.NOFOLLOW_LINKS),"Output must be fresh and request-owned");
        var image=existing(q.getComposedImage(),false);owned(image);owned(image.getParent());
        require(image.getParent().equals(parent.resolve("inputs")),"Image must be in owned inputs");
        require(BreathRepairPilot.digest(image).equals(q.getComposedImageSha256()),"Source hash changed");
        require(q.getWidth()==768&&q.getHeight()==448&&q.getFramesPerSecond()==25&&q.getExpectedFrameCount()==97&&q.getDurationMillis()==3880,"Only the approved 97-frame/3.88s scope is allowed");
        require(q.getPrompt().length()>0&&q.getPrompt().length()<=20000&&q.getPrompt().indexOf('\0')<0,"Invalid prompt");
        require(q.getPort()==8192&&q.getPollIntervalMillis()==2000,"Changed runtime connection scope");
        var support=existing(q.getApplicationSupportRoot(),true);var media=existing(q.getMediaToolsDirectory(),true);
        List<Path> protectedRoots=new ArrayList<>();
        for(var value:q.getProtectedMidiRoots())protectedRoots.add(existing(value,true));
        require(!protectedRoots.isEmpty(),"Protected MIDI roots missing");
        for(var root:protectedRoots)for(var selected:List.of(parent,output,image,support,media))
            require(!selected.startsWith(root)&&!root.startsWith(selected),"Protected MIDI overlap");
        require(!output.startsWith(support)&&!support.startsWith(output),"Runtime and evidence overlap");
        return new ValidatedRequest(path,parent,q.getRunId(),support,output,media,image,q.getPrompt(),768,448,3880,25,97,8192,2000,protectedRoots);
    }
}
