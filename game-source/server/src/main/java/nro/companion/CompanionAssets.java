package nro.companion;

import java.io.*;
import java.util.*;
import org.json.*;
import nro.models.Part;
import nro.models.PartManager;

/** Namespaced sprites and appended part IDs: no foreign template/part overwrite. */
public final class CompanionAssets {
    private static final JSONObject manifest=readManifest();
    private static short[] zeno;
    private static JSONObject readManifest(){try(InputStream in=CompanionAssets.class.getResourceAsStream("/companion/assets.json")){if(in==null)throw new IOException("Companion assets missing");return new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}catch(IOException e){throw new IllegalStateException(e);}}
    public static void install(){
        if(zeno!=null)return;
        PartManager pm=PartManager.getInstance();
        for(int i=0;i<pm.getParts().size();i++)if(pm.get(i).getId()!=i)throw new IllegalStateException("Non-contiguous base part IDs");
        for(int i=0;i<3;i++)if(pm.find(876+i)==null||pm.find(876+i).getType()!=i)throw new IllegalStateException("Jiren base parts unavailable");
        JSONArray source=manifest.getJSONArray("parts");zeno=new short[3];
        for(int i=0;i<3;i++){
            JSONObject obj=source.getJSONObject(i);JSONArray rows=obj.getJSONArray("data");int[][] data=new int[rows.length()][3];
            for(int j=0;j<rows.length();j++)for(int k=0;k<3;k++)data[j][k]=rows.getJSONArray(j).getInt(k);
            Part p=new Part();p.setId((short)pm.getParts().size());p.setType((byte)obj.getInt("type"));p.setPartData(data);zeno[i]=p.getId();pm.add(p);
        }pm.setData();
    }
    public static short[] outfit(String species){org.json.JSONObject vip=nro.live.GrowthService.vip(species);if(vip!=null){org.json.JSONArray a=vip.getJSONArray("outfit");return new short[]{(short)a.getInt(0),(short)a.getInt(1),(short)a.getInt(2)};}return species.equals("ZENO")?zeno:species.equals("JIREN")?new short[]{876,877,878}:new short[]{297,298,299};}
    public static byte[] icon(int zoom,int id){if(!manifest.getJSONObject("icons").toMap().containsValue(id))return null;
        try(InputStream in=CompanionAssets.class.getResourceAsStream("/companion/"+zoom+"/"+id+".png")){if(in==null)throw new IOException("Missing sprite fragment");return in.readAllBytes();}catch(IOException e){throw new IllegalStateException(e);}}
    public static void versions(byte[][] versions){for(int z=0;z<4;z++)for(String old:manifest.getJSONObject("icons").keySet()){
        int id=manifest.getJSONObject("icons").getInt(old);if(versions[z].length<=id)versions[z]=Arrays.copyOf(versions[z],id+1);
        byte previous=versions[z][id];byte next=(byte)(icon(z+1,id).length%126+1);if(next==previous)next=(byte)(next%126+1);versions[z][id]=next;
    }}
}
