package nro.live;
import java.sql.*;
import org.json.*;
import nro.jdbc.DBHika;
import nro.models.item.ItemTemplate;
import nro.server.Manager;
import nro.admin.OnlineRewards;
public final class GameContent {
    public static void start()throws Exception{
        GrowthService.reload();
        try(Connection c=DBHika.getConnection()){
            for(int id=2154;id<=2158;id++){String name=id==2154?"Trứng Ma Bư hỏng":"Bùa luyện tiềm năng x"+(id-2153);String description=id==2154?"Trứng hỏng không thể ấp; không giả làm trứng nguyên vẹn":"Dùng tăng thêm "+(id-2154)*100+"% TN/SM trong 1 giờ. Cùng loại nối thời gian, khác loại cộng phần tăng.";int icon=id==2154?2164:1403;
                if(Manager.ITEM_TEMPLATES.size()==id){ItemTemplate t=new ItemTemplate();t.id=(short)id;t.name=name;t.description=description;t.type=27;t.gender=3;t.iconID=(short)icon;t.part=-1;Manager.ITEM_TEMPLATES.add(t);}else if(Manager.ITEM_TEMPLATES.size()<=id||!Manager.ITEM_TEMPLATES.get(id).name.equals(name))throw new IllegalStateException("Custom item ID conflict: "+id);
                OnlineRewards.execute(c,"INSERT IGNORE INTO item_template(id,TYPE,gender,NAME,description,icon_id,part,is_up_to_up,power_require) VALUES(?,27,3,?,?,?,-1,0,0)",id,name,description,icon);
            }
            var egg=Manager.ITEM_TEMPLATES.get(568);egg.name="Trứng Ma Bư nguyên vẹn";egg.description="Có thể giao dịch trước khi dùng. Dùng từ hành trang để bắt đầu ấp.";
            nro.admin.StarterCodes.install(c);
        }
        TrainingWorld.reload();
    }
}
