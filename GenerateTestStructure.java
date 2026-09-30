import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/** Java 21 source launcher: java scripts/GenerateTestStructure.java (from project root). */
class GenerateTestStructure {
    public static void main(String[] args) throws Exception {
        Path file = Path.of("src/main/resources/data/worldeater/structure/test_empty.nbt");
        Files.createDirectories(file.getParent());
        try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            out.writeByte(10); out.writeUTF("");
            integer(out, "DataVersion", 3955);
            list(out, "size", 3, 3); out.writeInt(12); out.writeInt(8); out.writeInt(12);
            list(out, "palette", 10, 1);
            out.writeByte(8); out.writeUTF("Name"); out.writeUTF("minecraft:air"); out.writeByte(0);
            list(out, "blocks", 10, 12 * 8 * 12);
            for (int x = 0; x < 12; x++) for (int y = 0; y < 8; y++) for (int z = 0; z < 12; z++) {
                list(out, "pos", 3, 3); out.writeInt(x); out.writeInt(y); out.writeInt(z);
                integer(out, "state", 0); out.writeByte(0);
            }
            list(out, "entities", 10, 0); out.writeByte(0);
        }
    }
    private static void integer(DataOutputStream out, String name, int value) throws Exception {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }
    private static void list(DataOutputStream out, String name, int type, int length) throws Exception {
        out.writeByte(9); out.writeUTF(name); out.writeByte(type); out.writeInt(length);
    }
}
