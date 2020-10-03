package net.minestom.server.resourcepack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.CustomBlock;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public class ResourcePackManager {

	/**
	 * The max amount of blocks you can add using note blocks
	 * <p>
	 * 16 instruments * 25 pitches * 2 powered states
	 */
	private static final int LIMIT = 800;
	private static final int START_ID = Block.NOTE_BLOCK.getAlternatives().get(0).getId();
	private int currentId = 0;
	private Gson gson = new GsonBuilder().disableHtmlEscaping().create();
	private Variants variants = new Variants();

	public ResourcePackManager() {
		//TODO re-add bindModelToBlock(null, null, new File("note_block"));
		//HashMap<String, String> map = new HashMap<>();
		//map.put("model", "minecraft:block/note_block");
		//variants.getVariants().put("", map);
	}

	public synchronized void bindModelToBlock(CustomBlock block, Consumer<HashMap<String, String>> variantsConsumer, File model) {
		File dir = new File("temp_pack/assets/minecraft/models/block/");
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				throw new RuntimeException("could not create directory");
			}
		}
		dir = new File("temp_pack/assets/minecraft/blockstates/");
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				throw new RuntimeException("could not create directory");
			}
		}
		if (++currentId > LIMIT) {
			throw new IllegalStateException("Too many blocks have been added!");
		}
		short id = (short) (currentId + START_ID);
		if (block != null)
			block.setDefaultBlockStateId(id);
		else
			log.warn("block was null in bindModelToBlock this might be an error");
		StringBuilder sb = new StringBuilder();
		Arrays.asList(Block.NOTE_BLOCK.getAlternative(id).getProperties()).forEach(s -> sb.append(s).append(","));
		String s = sb.toString();
		HashMap<String, String> variant = new HashMap<>();
		variant.put("model", "minecraft:block/" + model.getName().substring(0, model.getName().length() - 5));
		if (variantsConsumer != null)
			variantsConsumer.accept(variant);
		variants.getVariants().put(s.substring(0, s.length() - 1), variant);
		try {
			Files.copy(model.toPath(), Path.of("temp_pack/assets/minecraft/models/block/" + model.getName()), StandardCopyOption.REPLACE_EXISTING);
			Writer writer = Files.newBufferedWriter(Paths.get("temp_pack/assets/minecraft/blockstates/note_block.json"));
			gson.toJson(variants, writer);
			writer.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public synchronized void addTextures(File... files) {
		File dir = new File("temp_pack/assets/minecraft/textures/block/");
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				throw new RuntimeException("could not create directory");
			}
		}
		for (File file : files) {
			try {
				Files.copy(file.toPath(), Path.of("temp_pack/assets/minecraft/textures/block/" + file.getName()), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public void compile() {
		try {
			Writer writer = Files.newBufferedWriter(Paths.get("temp_pack/pack.mcmeta"));
			writer.write("{\n" +
					"    \"pack\": {\n" +
					"        \"pack_format\": 6,\n" +
					"        \"description\": \"Test Resource Pack\"\n" +
					"    }\n" +
					"}");
			writer.close();
			zipFolder("temp_pack", "pack.zip");
			Files.walk(Path.of("temp_pack"))
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
			HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
			server.createContext("/resources.zip", t -> {
				Headers h = t.getResponseHeaders();
				h.add("Content-Type", "application/zip");
				File file = new File("pack.zip");
				byte[] bytearray = new byte[(int) file.length()];
				FileInputStream fis = new FileInputStream(file);
				BufferedInputStream bis = new BufferedInputStream(fis);
				bis.read(bytearray, 0, bytearray.length);
				t.sendResponseHeaders(200, file.length());
				OutputStream os = t.getResponseBody();
				os.write(bytearray, 0, bytearray.length);
				os.close();
			});
			server.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//https://www.baeldung.com/java-compress-and-uncompress
	private static void zipFolder(String in, String out) {
		try {
			ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(out));
			File fileToZip = new File(in);
			zipFile(fileToZip, fileToZip.getName(), zipOut, true);
			zipOut.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut, boolean top) throws IOException {
		if (fileToZip.isHidden()) {
			return;
		}
		if (fileToZip.isDirectory()) {
			if (!top)
				if (fileName.endsWith("/")) {
					zipOut.putNextEntry(new ZipEntry(fileName));
				} else {
					zipOut.putNextEntry(new ZipEntry(fileName + "/"));
				}
			zipOut.closeEntry();
			File[] children = fileToZip.listFiles();
			for (File childFile : children) {
				zipFile(childFile, (top ? "" : fileName + "/") + childFile.getName(), zipOut, false);
			}
			return;
		}
		FileInputStream fis = new FileInputStream(fileToZip);
		ZipEntry zipEntry = new ZipEntry(fileName);
		zipOut.putNextEntry(zipEntry);
		byte[] bytes = new byte[1024];
		int length;
		while ((length = fis.read(bytes)) >= 0) {
			zipOut.write(bytes, 0, length);
		}
		fis.close();
	}

}
