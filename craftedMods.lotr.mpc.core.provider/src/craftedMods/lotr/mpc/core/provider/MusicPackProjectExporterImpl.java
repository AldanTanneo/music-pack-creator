package craftedMods.lotr.mpc.core.provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.log.FormatterLogger;
import org.osgi.service.log.LoggerFactory;

import craftedMods.eventManager.api.EventInfo;
import craftedMods.eventManager.api.EventManager;
import craftedMods.eventManager.api.EventProperties;
import craftedMods.eventManager.api.PropertyKey;
import craftedMods.eventManager.api.WriteableEventProperties;
import craftedMods.eventManager.base.DefaultWriteableEventProperties;
import craftedMods.eventManager.base.EventUtils;
import craftedMods.fileManager.api.FileManager;
import craftedMods.lotr.mpc.core.api.MusicPack;
import craftedMods.lotr.mpc.core.api.MusicPackProject;
import craftedMods.lotr.mpc.core.api.MusicPackProjectExporter;
import craftedMods.lotr.mpc.core.api.MusicPackProjectManager;
import craftedMods.lotr.mpc.core.api.Track;
import craftedMods.lotr.mpc.persistence.api.TrackStore;
import craftedMods.lotr.mpc.persistence.api.TrackStoreManager;
import craftedMods.utils.Utils;
import craftedMods.utils.data.ExtendedProperties;
import craftedMods.versionChecker.api.SemanticVersion;

@Component
public class MusicPackProjectExporterImpl implements MusicPackProjectExporter {

	@Reference(service = LoggerFactory.class)
	private FormatterLogger logger;

	@Reference(target = "(application=mpc)")
	private SemanticVersion version;

	@Reference
	private EventManager eventManager;

	@Reference
	private FileManager fileManager;

	@Reference
	private MusicPackJSONFileWriter writer;

	@Reference
	private TrackStoreManager trackStoreManager;

	@Reference
	private MusicPackProjectManager musicPackProjectManager;

	@Override
	public void exportMusicPackProject(Path exportLocation, MusicPackProject project) {
		Objects.requireNonNull(exportLocation);
		Objects.requireNonNull(project);
		if (!musicPackProjectManager.getRegisteredMusicPackProjects().contains(project))
			throw new IllegalArgumentException(
					String.format("The Music Pack Project \"%s\" isn't registered", project.getName()));
		MusicPack pack = project.getMusicPack();
		boolean delete = false;
		boolean cancel = false;
		try {
			if (fileManager.exists(exportLocation)) {
				if (!EventUtils.proceed(this.dispatchEvent(EXPORT_LOCATION_EXISTS_EVENT, exportLocation, project),
						MusicPackProjectExporter.EXPORT_LOCATION_EXISTS_EVENT_RESULT_OVERRIDE, false)) {
					cancel = true;
				} else {
					fileManager.deleteFile(exportLocation); // Delete the file to prevent conflicts
				}
			}
			
			if (!cancel) {
				Map<String, Object> env = new HashMap<>();
				env.put("create", "true"); // The .zip file will be created by the file system
				env.put("useTempFile", Boolean.TRUE); // Fixes the memory leaks

				try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + exportLocation.toUri()), env)) {
					dispatchEvent(CREATING_FILE_EVENT, exportLocation, project,
							MusicPackProjectExporter.CREATING_FILE_EVENT_FILENAME, MusicPackProjectExporter.BASE_FILE);
					Path musicJson = zip.getPath(".", MusicPackProjectExporter.BASE_FILE);
					fileManager.write(musicJson, writer.writeJSONFile(pack.getTracks()));

					dispatchEvent(CREATING_FILE_EVENT, exportLocation, project,
							MusicPackProjectExporter.CREATING_FILE_EVENT_FILENAME, MusicPackProjectExporter.PACK_FILE);
					Path packProperties = zip.getPath(".", MusicPackProjectExporter.PACK_FILE);

					ExtendedProperties props = new ExtendedProperties();
					props.setString(MusicPackProjectExporter.PACK_PROJECT_NAME_KEY, project.getName());
					props.setInteger(MusicPackProjectExporter.PACK_TRACKS_KEY, pack.getTracks().size());
					props.putAll(project.getProperties());
					try (ByteArrayOutputStream out2 = new ByteArrayOutputStream()) {
						props.store(out2, "This Music Pack was generated by the Music Pack Creator ("
								+ version.toString()
								+ ") made by Crafted_Mods (see http://lotrminecraftmod.wikia.com/wiki/Thread:308819 and https://github.com/CraftedMods)");
						fileManager.write(packProperties, out2.toByteArray());
					}
					Path tracksDir = zip.getPath(".", MusicPackProjectExporter.TRACKS_DIR);
					fileManager.createDir(tracksDir);
					TrackStore trackStore = trackStoreManager.getTrackStore(project);
					for (Track track : pack.getTracks()) {
						Path trackZipLocation = tracksDir.resolve(track.getName());

						try (InputStream trackIn = trackStore.openInputStream(track.getName());
								OutputStream trackOut = fileManager.newOutputStream(trackZipLocation)) {

							if (!EventUtils.proceed(
									this.dispatchEvent(COPYING_TRACK_EVENT, exportLocation, project,
											MusicPackProjectExporter.COPYING_TRACK_EVENT_TRACK_NAME, track.getName()),
									MusicPackProjectExporter.COPYING_TRACK_EVENT_RESULT_PROCEED)) {
								delete = true;
								break;
							}

							Utils.writeFromInputStreamToOutputStream(trackIn, trackOut);
						}
					}
				}
			}
			if (!cancel && !delete && EventUtils.proceed(this.dispatchEvent(PRE_SUCCESS_EVENT, exportLocation, project),
					MusicPackProjectExporter.PRE_SUCCESS_EVENT_RESULT_PROCEED)) {
				dispatchEvent(SUCCESS_EVENT, exportLocation, project);
				this.logger.info("Successfully exported the Music Pack Project \"%s\" to \"%s\"", project.getName(),
						exportLocation);
			} else {
				/*
				 * Cancel is true if overriding an existing file wasn't permitted, otherwise
				 * false. The PRE_SUCCESS_EVENT could also cancel the exportation, so in that
				 * case delete has to be set to true too.
				 */
				delete = !cancel;
				this.dispatchEvent(CANCEL_EVENT, exportLocation, project);
			}
		} catch (Exception e) {
			delete = true;
			this.dispatchEvent(ERROR_EVENT, exportLocation, project, MusicPackProjectExporter.ERROR_EVENT_EXCEPTION, e);

			this.logger.error("Couldn't export the Music Pack Project \"%s\" to \"%s\"", project.getName(),
					exportLocation.toString(), e);
		} finally {
			if (delete) {
				try {
					fileManager.deleteFile(exportLocation);
				} catch (IOException e) {
					logger.error("Couldn't delete the exported but corrupted Music Pack from \"%s\"", exportLocation,
							e);
				}
			}
		}
	}

	private <T> Collection<EventProperties> dispatchEvent(EventInfo info, Path path, MusicPackProject project) {
		return this.dispatchEvent(info, path, project, null, null);
	}

	private <T> Collection<EventProperties> dispatchEvent(EventInfo info, Path path, MusicPackProject project,
			PropertyKey<T> key, T value) {
		WriteableEventProperties properties = new DefaultWriteableEventProperties();
		properties.put(MusicPackProjectExporter.COMMON_EVENT_LOCATION, path);
		properties.put(MusicPackProjectExporter.COMMON_EVENT_MUSIC_PACK_PROJECT, project);
		if (key != null)
			properties.put(key, value);
		return this.eventManager.dispatchEvent(info, properties);
	}

}
