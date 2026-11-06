package dev.faketbm;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class TbmScreen extends Screen {
    private final EditBox[] fields = new EditBox[9];
    private static final String[] LABELS={"X1", "Z1", "X2", "Z2", "目标方块 Y", "名字前缀", "假人数量", "每次秒数", "命令间隔 tick"};
    TbmScreen() { super(Component.literal("假人盾构机")); }
    @Override protected void init() {
        int left=width/2-150, top=Math.max(10, (height-230)/2);
        String[] values={""+FakeTbm.minX,""+FakeTbm.minZ,""+FakeTbm.maxX,""+FakeTbm.maxZ,""+FakeTbm.y,FakeTbm.prefix,""+FakeTbm.count,""+FakeTbm.seconds,""+FakeTbm.interval};
        for (int i=0;i<9;i++) {
            int col=i%2, row=i/2;
            EditBox box=new EditBox(font,left+col*155,top+row*30+10,145,20,Component.literal(LABELS[i]));
            box.setMaxLength(20); box.setValue(values[i]); fields[i]=addRenderableWidget(box);
        }
        addRenderableWidget(Button.builder(Component.literal("应用间隔"), b -> FakeTbm.command(null,"interval "+fields[8].getValue())).bounds(left+155,top+130,145,20).build());
        addRenderableWidget(Button.builder(Component.literal("保存设置"), b -> apply()).bounds(left,top+155,95,20).build());
        addRenderableWidget(Button.builder(Component.literal("保存并开始"), b -> { if (apply()) FakeTbm.command(null,"start"); }).bounds(left+102,top+155,95,20).build());
        addRenderableWidget(Button.builder(Component.literal("停止"), b -> FakeTbm.command(null,"stop")).bounds(left+204,top+155,95,20).build());
        addRenderableWidget(Button.builder(Component.literal("确认原服后恢复"), b -> FakeTbm.command(null,"resume")).bounds(left,top+179,145,20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose()).bounds(left+155,top+179,145,20).build());
    }
    private boolean apply() {
        String area="area "+fields[0].getValue()+" "+fields[1].getValue()+" "+fields[2].getValue()+" "+fields[3].getValue()+" "+fields[4].getValue();
        String config="config "+fields[5].getValue()+" "+fields[6].getValue()+" "+fields[7].getValue();
        try {
            if (FakeTbm.running) throw new IllegalArgumentException("运行中不能修改配置");
            for (int i=0;i<5;i++) {
                int v=Integer.parseInt(fields[i].getValue());
                if (i<4 && Math.abs((long)v)>30000000 || i==4 && (v < -2048 || v > 2047)) throw new IllegalArgumentException("坐标超出范围");
            }
            int n=Integer.parseInt(fields[6].getValue()), sec=Integer.parseInt(fields[7].getValue());
            int interval=Integer.parseInt(fields[8].getValue());
            if (interval<1 || interval>1200) throw new IllegalArgumentException("命令间隔须为 1–1200 tick");
            String pre=fields[5].getValue();
            if (n<1 || n>100 || sec<1 || sec>86400 || !pre.matches("[A-Za-z0-9_]+") || pre.length()+Integer.toString(n).length()>16)
                throw new IllegalArgumentException("请检查名字、数量和时间");
        } catch (IllegalArgumentException e) {
            FakeTbm.lastMessage=e instanceof NumberFormatException ? "请输入有效数字" : e.getMessage();
            return false;
        }
        return FakeTbm.command(null,area)==1 && FakeTbm.command(null,config)==1 && FakeTbm.command(null,"interval "+fields[8].getValue())==1;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        int left=width/2-150, top=Math.max(10,(height-230)/2);
        graphics.centeredText(font,title,width/2,top-10,0xffffffff);
        for (int i=0;i<9;i++) graphics.text(font,LABELS[i],left+(i%2)*155,top+(i/2)*30,0xffdddddd);
        String status=FakeTbm.running ? (FakeTbm.stopping ? "停止中" : "任务运行中")+"：已分配 "+FakeTbm.next+"/"+FakeTbm.total : "未运行";
        graphics.centeredText(font,status,width/2,top+203,0xffffffff);
        graphics.centeredText(font,FakeTbm.lastMessage,width/2,top+215,0xffffff80);
    }
    @Override public boolean isPauseScreen() { return false; }
}
