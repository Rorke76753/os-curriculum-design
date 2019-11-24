package disk.service;

import disk.bean.DiskBlock;
import disk.util.DiskUtils;

import java.util.ArrayList;

/**
 * @author Rorke
 * @Date 2019/11/4
 * 为disk界面服务
 */
public class DiskService {
    private static final int BLOCKS_SIZE = 256;
    private final int FAT_SIZE = 2;
    private static ArrayList<DiskBlock> blocks = new ArrayList<>(BLOCKS_SIZE);
    private DiskBlock[] fatBlocks = new DiskBlock[FAT_SIZE];
    private DiskUtils diskUtils;
    private static DiskService diskService = new DiskService();

    private DiskService() {
        String diskPath = "simulateDisk/Disk.txt";
        this.diskUtils = new DiskUtils(diskPath);
        readDisk();
    }

    private void setFileAccessTableBlock() {
        for (int i = 0; i < FAT_SIZE; i++) {
            fatBlocks[i] = blocks.get(i);
        }
    }

    /**
     * 获得FAT
     * @return FAT
     */
    public DiskBlock[] getFATBlocks(){
        return fatBlocks;
    }
    /**
     * 系统启动时执行，读取disk的情况
     */
    private void readDisk() {
        diskUtils.read(blocks);
        OccupyService occupyService = new OccupyService();
        boolean[] occupyList = occupyService.getOccupy();
        for (int i = 0; i < BLOCKS_SIZE; i++) {
            blocks.get(i).setOccupy(occupyList[i]);
        }
        setFileAccessTableBlock();
    }

    /**
     * 有文件改变时，保存对磁盘的修改
     */
    public void modifyDisk() {
        diskUtils.write(blocks);
        OccupyService occupyService = new OccupyService();
        occupyService.writeOccupy(blocks);
    }

    /**
     * 获得磁盘占用情况
     * @return 磁盘占用情况表，每一个元素都表示该块被占用了多少字节
     */
    public ArrayList<Integer> getDiskStatus() {
        ArrayList<Integer> statusList = new ArrayList<>();
        for (DiskBlock b : blocks) {
            statusList.add(b.getBlockStatus());
        }
        return statusList;
    }

    public boolean[] getOccupyStatus() {
        boolean[] occupyList = new boolean[BLOCKS_SIZE];
        for (int i = 0; i < BLOCKS_SIZE; i++) {
            occupyList[i] = blocks.get(i).isOccupy();
        }
        return occupyList;
    }
    /**
     * 获取磁盘块
     * @return 磁盘块，DiskBlock
     */
    public DiskBlock getDiskBlock(int index){
        return blocks.get(index);
    }
    public int getBlockIndex(DiskBlock block){
        return blocks.indexOf(block);
    }
    /**
     * 单例模式
     * @return 确保程序中所有diskService实例为同一个
     */
    public static DiskService getInstance() {
        if(diskService==null){
            diskService = new DiskService();
        }
        return diskService;
    }
}
