package craftedMods.lotr.mpc.compatibility.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.easymock.MockType;
import org.easymock.TestSubject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.osgi.service.log.LogService;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import craftedMods.eventManager.api.EventManager;
import craftedMods.eventManager.api.EventProperties;
import craftedMods.eventManager.api.WriteableEventProperties;
import craftedMods.eventManager.base.DefaultWriteableEventProperties;
import craftedMods.fileManager.api.FileManager;
import craftedMods.lotr.mpc.compatibility.api.MusicPackProjectCompatibilityManager;
import craftedMods.lotr.mpc.core.api.MusicPack;
import craftedMods.lotr.mpc.core.api.MusicPackProject;
import craftedMods.lotr.mpc.core.api.MusicPackProjectFactory;
import craftedMods.lotr.mpc.core.api.Track;
import craftedMods.lotr.mpc.core.base.DefaultRegion;
import craftedMods.lotr.mpc.core.base.DefaultTrack;
import craftedMods.lotr.mpc.persistence.api.TrackStore;
import craftedMods.lotr.mpc.persistence.api.TrackStoreManager;
import craftedMods.utils.Utils;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ MusicPackProjectCompatibilityManagerImpl.class, Utils.class })
public class MusicPackProjectCompatibilityManagerImplTest extends EasyMockSupport {

	@TestSubject
	public MusicPackProjectCompatibilityManagerImpl compatibilityManager = new MusicPackProjectCompatibilityManagerImpl();

	@Mock
	private EventManager mockEventManager;

	@Mock
	private MusicPackProjectFactory mockMusicPackProjectFactory;

	@Mock(type = MockType.NICE)
	private LogService mockLogService;

	@Mock
	private FileManager mockFileManager;

	@Mock
	private SerializedWorkspaceToJSONConverter mockSerializedConverter;

	@Mock
	private TrackStoreManager mockTrackStoreManager;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Path projectsDir;
	private Path oldProjectFile;

	private Map<Path, Collection<craftedMods.lotrTools.musicPackCreator.data.Track>> oldProjects;

	@Before
	public void setup() {
		projectsDir = folder.getRoot().toPath();
		oldProjectFile = projectsDir.resolve(SerializedWorkspaceToJSONConverter.OLD_PROJECT_FILE);

		oldProjects = new HashMap<>();

		EasyMock.expect(mockSerializedConverter.getOldProjects()).andStubReturn(oldProjects);
	}

	@Test
	public void testApplyPreLoadFixesSerializedWorkspaceNoOldFile() {
		EasyMock.expect(mockFileManager.exists(oldProjectFile)).andReturn(false).once();

		replayAll();

		compatibilityManager.applyPreLoadFixes(projectsDir);

		verifyAll();
	}

	@Test
	public void testApplyPreLoadFixesSerializedWorkspace() throws IOException {
		EasyMock.expect(mockFileManager.exists(oldProjectFile)).andReturn(true).once();

		Capture<WriteableEventProperties> capturedProperties1 = Capture.newInstance();

		Collection<EventProperties> results = new ArrayList<>();

		WriteableEventProperties proceedProperties = new DefaultWriteableEventProperties();
		proceedProperties.put(
				MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_DETECTED_EVENT_RESULT_PROCEED,
				Boolean.TRUE);

		results.add(proceedProperties);

		EasyMock.expect(mockEventManager.dispatchEvent(
				EasyMock.eq(MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_DETECTED_EVENT),
				EasyMock.capture(capturedProperties1))).andReturn(results).once();

		mockSerializedConverter.convertWorkspace(projectsDir);
		EasyMock.expectLastCall().once();

		Capture<WriteableEventProperties> capturedProperties2 = Capture.newInstance();

		EasyMock.expect(mockEventManager.dispatchEvent(
				EasyMock.eq(MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_CONVERTED_EVENT),
				EasyMock.capture(capturedProperties2))).andReturn(null).once();

		replayAll();

		compatibilityManager.applyPreLoadFixes(projectsDir);

		WriteableEventProperties properties1 = capturedProperties1.getValue();
		WriteableEventProperties properties2 = capturedProperties2.getValue();

		Assert.assertEquals(projectsDir, properties1
				.getProperty(MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_DETECTED_EVENT_PATH));
		Assert.assertEquals(projectsDir, properties2
				.getProperty(MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_CONVERTED_EVENT_PATH));

		verifyAll();
	}

	@Test
	public void testApplyPreLoadFixesSerializedWorkspaceNoFeedbackFromListeners() throws IOException {
		EasyMock.expect(mockFileManager.exists(oldProjectFile)).andReturn(true).once();

		EasyMock.expect(mockEventManager.dispatchEvent(
				EasyMock.eq(MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_DETECTED_EVENT),
				EasyMock.anyObject(WriteableEventProperties.class))).andReturn(new ArrayList<>()).once();

		mockSerializedConverter.convertWorkspace(projectsDir);
		EasyMock.expectLastCall().once();

		EasyMock.expect(mockEventManager.dispatchEvent(
				EasyMock.eq(MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_CONVERTED_EVENT),
				EasyMock.anyObject(WriteableEventProperties.class))).andReturn(null).once();

		replayAll();

		compatibilityManager.applyPreLoadFixes(projectsDir);

		verifyAll();
	}

	@Test
	public void testApplyPreLoadFixesSerializedWorkspaceCancel() {
		EasyMock.expect(mockFileManager.exists(oldProjectFile)).andReturn(true).once();

		Capture<WriteableEventProperties> capturedProperties = Capture.newInstance();

		Collection<EventProperties> results = new ArrayList<>();

		WriteableEventProperties proceedProperties = new DefaultWriteableEventProperties();
		proceedProperties.put(
				MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_DETECTED_EVENT_RESULT_PROCEED,
				Boolean.TRUE);

		WriteableEventProperties cancelProperties = new DefaultWriteableEventProperties();
		proceedProperties.put(
				MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_DETECTED_EVENT_RESULT_PROCEED,
				Boolean.FALSE);

		results.add(proceedProperties);
		results.add(cancelProperties);

		EasyMock.expect(mockEventManager.dispatchEvent(
				EasyMock.eq(MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_DETECTED_EVENT),
				EasyMock.capture(capturedProperties))).andReturn(results).once();

		replayAll();

		compatibilityManager.applyPreLoadFixes(projectsDir);

		verifyAll();
	}

	@Test
	public void testApplyPreLoadFixesSerializedWorkspaceConversionException() throws IOException {
		EasyMock.expect(mockFileManager.exists(oldProjectFile)).andReturn(true).once();

		EasyMock.expect(mockEventManager.dispatchEvent(
				EasyMock.eq(MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_DETECTED_EVENT),
				EasyMock.anyObject(WriteableEventProperties.class))).andReturn(new ArrayList<>()).once();

		RuntimeException thrownException = new RuntimeException("Error");

		mockSerializedConverter.convertWorkspace(projectsDir);
		EasyMock.expectLastCall().andThrow(thrownException).once();

		Capture<WriteableEventProperties> capturedProperties = Capture.newInstance();

		EasyMock.expect(mockEventManager.dispatchEvent(
				EasyMock.eq(MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_ERROR_EVENT),
				EasyMock.capture(capturedProperties))).andReturn(null).once();

		replayAll();

		compatibilityManager.applyPreLoadFixes(projectsDir);

		WriteableEventProperties properties = capturedProperties.getValue();

		Assert.assertEquals(thrownException, properties
				.getProperty(MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_ERROR_EVENT_EXCEPTION));
		Assert.assertEquals(projectsDir, properties
				.getProperty(MusicPackProjectCompatibilityManager.PRE_LOAD_SERIALIZED_WORKSPACE_ERROR_EVENT_PATH));

		verifyAll();
	}

	@Test(expected = NullPointerException.class)
	public void testApplyPreLoadFixesNull() {
		compatibilityManager.applyPreLoadFixes(null);
	}

	@Test
	public void testApplyPostLoadFixesAndrastFix() {
		Path projectFolder = Paths.get("project1");
		MusicPackProject mockMusicPackProject = createMockProject();
		MusicPack mockMusicPack = this.createMock(MusicPack.class);
		List<Track> tracks = new ArrayList<>();

		EasyMock.reset(mockMusicPackProject);

		EasyMock.expect(mockMusicPackProject.getMusicPack()).andStubReturn(mockMusicPack);
		EasyMock.expect(mockMusicPack.getTracks()).andStubReturn(tracks);
		EasyMock.expect(mockMusicPackProject.getName()).andStubReturn("Name");

		tracks.add(new DefaultTrack("track1", "title",
				Arrays.asList(new DefaultRegion("andrast", Arrays.asList("test"), Arrays.asList(), null)),
				Arrays.asList()));
		tracks.add(new DefaultTrack("track2", "2title",
				Arrays.asList(new DefaultRegion("all", Arrays.asList(), Arrays.asList(), null)), Arrays.asList()));
		tracks.add(new DefaultTrack("track3", "2titl3e",
				Arrays.asList(new DefaultRegion("andrast", Arrays.asList(), Arrays.asList(), null)),
				Arrays.asList("s")));

		Capture<WriteableEventProperties> capturedProject = Capture.newInstance();

		EasyMock.expect(mockEventManager.dispatchEvent(
				EasyMock.eq(MusicPackProjectCompatibilityManager.POST_LOAD_ANDRAST_FIX_EVENT),
				EasyMock.capture(capturedProject))).andReturn(null).times(2);

		this.replayAll();

		compatibilityManager.applyPostLoadFixes(projectFolder, mockMusicPackProject, "Music Pack Creator Beta 2.0");

		WriteableEventProperties properties = capturedProject.getValue();

		Assert.assertEquals(mockMusicPackProject, properties
				.getProperty(MusicPackProjectCompatibilityManager.POST_LOAD_ANDRAST_FIX_EVENT_MUSIC_PACK_PROJECT));

		this.verifyAll();
	}

	@Test
	public void testApplyPostLoadFixesAndrastFixNoTracksToFix() {
		Path projectFolder = Paths.get("project1");
		MusicPackProject mockMusicPackProject = createMockProject();

		EasyMock.reset(mockMusicPackProject);

		MusicPack mockMusicPack = this.createMock(MusicPack.class);
		List<Track> tracks = new ArrayList<>();

		EasyMock.expect(mockMusicPackProject.getMusicPack()).andStubReturn(mockMusicPack);
		EasyMock.expect(mockMusicPack.getTracks()).andStubReturn(tracks);

		tracks.add(new DefaultTrack("track4", "title",
				Arrays.asList(new DefaultRegion("all", Arrays.asList(), Arrays.asList(), null)), Arrays.asList()));

		this.replayAll();

		compatibilityManager.applyPostLoadFixes(projectFolder, mockMusicPackProject, "Music Pack Creator Beta 2.0");

		this.verifyAll();
	}

	@Test
	public void testApplyPostLoadFixesAndrastFixWrongVersion() {
		Path projectFolder = Paths.get("project1");
		MusicPackProject mockMusicPackProject = createMockProject();

		this.replayAll();

		compatibilityManager.applyPostLoadFixes(projectFolder, mockMusicPackProject, "Music Pack Creator Beta 3.3");

		this.verifyAll();
	}

	@Test
	public void testApplyPostLoadFixesAndrastFixUnprefixedVersion() {
		Path projectFolder = Paths.get("project1");
		MusicPackProject mockMusicPackProject = createMockProject();

		this.replayAll();

		compatibilityManager.applyPostLoadFixes(projectFolder, mockMusicPackProject, "2.0");

		this.verifyAll();
	}

	@Test
	public void testApplyPostLoadFixesAndrastFixNullVersion() {
		Path projectFolder = Paths.get("project1");
		MusicPackProject mockMusicPackProject = createMockProject();

		this.replayAll();

		compatibilityManager.applyPostLoadFixes(projectFolder, mockMusicPackProject, null);

		this.verifyAll();
	}

	@Test(expected = NullPointerException.class)
	public void testApplyPostLoadFixesNullPath() {
		compatibilityManager.applyPostLoadFixes(null, createMockProject(), "Version");
	}

	@Test(expected = NullPointerException.class)
	public void testApplyPostLoadFixesNullProject() {
		compatibilityManager.applyPostLoadFixes(Paths.get("project1"), null, "Version");
	}

	@Test
	public void testApplyPostLoadFixesCopyTracksNotInMap() {
		Path projectFolder = Paths.get("project1");
		MusicPackProject mockMusicPackProject = createMockProject();

		this.replayAll();

		compatibilityManager.applyPostLoadFixes(projectFolder, mockMusicPackProject, null);

		this.verifyAll();
	}

	@Test
	public void testApplyPostLoadFixesCopyTracksInMap() throws IOException {
		Path projectFolder = Paths.get("project1");
		MusicPackProject mockMusicPackProject = createMockProject();

		craftedMods.lotrTools.musicPackCreator.data.Track oldTrack1 = this
				.createMock(craftedMods.lotrTools.musicPackCreator.data.Track.class);
		craftedMods.lotrTools.musicPackCreator.data.Track oldTrack2 = this
				.createMock(craftedMods.lotrTools.musicPackCreator.data.Track.class);
		craftedMods.lotrTools.musicPackCreator.data.Track oldTrack3 = this
				.createMock(craftedMods.lotrTools.musicPackCreator.data.Track.class);

		Path trackPath1 = Paths.get("path1.ogg");
		Path trackPath2 = Paths.get("path2.ogg");
		Path trackPath3 = Paths.get("path3.ogg");

		EasyMock.expect(oldTrack1.getTrackPath()).andStubReturn(trackPath1);
		EasyMock.expect(oldTrack1.getFilename()).andStubReturn(trackPath1.getFileName().toString());
		EasyMock.expect(oldTrack2.getTrackPath()).andStubReturn(trackPath2);
		EasyMock.expect(oldTrack2.getFilename()).andStubReturn(trackPath2.getFileName().toString());
		EasyMock.expect(oldTrack3.getTrackPath()).andStubReturn(trackPath3);
		EasyMock.expect(oldTrack3.getFilename()).andStubReturn(trackPath3.getFileName().toString());

		TrackStore mockTrackStore = this.createMock(TrackStore.class);

		Collection<String> storedTracks = new ArrayList<>();

		EasyMock.expect(mockTrackStore.getStoredTracks()).andReturn(storedTracks).atLeastOnce();

		EasyMock.expect(mockTrackStoreManager.getTrackStore(mockMusicPackProject)).andReturn(mockTrackStore).once();

		mockTrackStore.refresh();
		EasyMock.expectLastCall().once();

		EasyMock.expect(mockFileManager.exists(trackPath1)).andStubReturn(true);
		EasyMock.expect(mockFileManager.exists(trackPath2)).andStubReturn(true);
		EasyMock.expect(mockFileManager.exists(trackPath3)).andStubReturn(true);

		InputStream mockInputStream1 = this.createMock(InputStream.class);
		InputStream mockInputStream2 = this.createMock(InputStream.class);
		InputStream mockInputStream3 = this.createMock(InputStream.class);

		EasyMock.expect(mockFileManager.newInputStream(trackPath1)).andReturn(mockInputStream1).once();
		EasyMock.expect(mockFileManager.newInputStream(trackPath2)).andReturn(mockInputStream2).once();
		EasyMock.expect(mockFileManager.newInputStream(trackPath3)).andReturn(mockInputStream3).once();

		mockInputStream1.close();
		EasyMock.expectLastCall().once();

		mockInputStream2.close();
		EasyMock.expectLastCall().once();

		mockInputStream3.close();
		EasyMock.expectLastCall().once();

		OutputStream mockOutputStream1 = this.createMock(OutputStream.class);
		OutputStream mockOutputStream2 = this.createMock(OutputStream.class);
		OutputStream mockOutputStream3 = this.createMock(OutputStream.class);

		EasyMock.expect(mockTrackStore.openOutputStream("path1.ogg")).andReturn(mockOutputStream1).once();
		EasyMock.expect(mockTrackStore.openOutputStream("path2.ogg")).andReturn(mockOutputStream2).once();
		EasyMock.expect(mockTrackStore.openOutputStream("path3.ogg")).andReturn(mockOutputStream3).once();

		mockOutputStream1.close();
		EasyMock.expectLastCall().once();

		mockOutputStream2.close();
		EasyMock.expectLastCall().once();

		mockOutputStream3.close();
		EasyMock.expectLastCall().once();

		PowerMock.mockStatic(Utils.class);

		Utils.writeFromInputStreamToOutputStream(mockInputStream1, mockOutputStream1);
		EasyMock.expectLastCall().once();

		Utils.writeFromInputStreamToOutputStream(mockInputStream2, mockOutputStream2);
		EasyMock.expectLastCall().once();

		Utils.writeFromInputStreamToOutputStream(mockInputStream3, mockOutputStream3);
		EasyMock.expectLastCall().once();

		this.replayAll();
		PowerMock.replayAll();

		oldProjects.put(projectFolder, Arrays.asList(oldTrack1, oldTrack2, oldTrack3));

		compatibilityManager.applyPostLoadFixes(projectFolder, mockMusicPackProject, null);

		Assert.assertTrue(oldProjects.isEmpty());

		this.verifyAll();
		PowerMock.verifyAll();
	}

	@Test
	public void testApplyPostLoadFixesCopyTracksInMapSomeNotExisting() throws IOException {
		Path projectFolder = Paths.get("project1");
		MusicPackProject mockMusicPackProject = createMockProject();

		craftedMods.lotrTools.musicPackCreator.data.Track oldTrack1 = this
				.createMock(craftedMods.lotrTools.musicPackCreator.data.Track.class);
		craftedMods.lotrTools.musicPackCreator.data.Track oldTrack2 = this
				.createMock(craftedMods.lotrTools.musicPackCreator.data.Track.class);
		craftedMods.lotrTools.musicPackCreator.data.Track oldTrack3 = this
				.createMock(craftedMods.lotrTools.musicPackCreator.data.Track.class);

		Path trackPath1 = Paths.get("path1.ogg");
		Path trackPath2 = Paths.get("path2.ogg");
		Path trackPath3 = Paths.get("path3.ogg");

		EasyMock.expect(oldTrack1.getTrackPath()).andStubReturn(trackPath1);
		EasyMock.expect(oldTrack1.getFilename()).andStubReturn(trackPath1.getFileName().toString());
		EasyMock.expect(oldTrack2.getTrackPath()).andStubReturn(trackPath2);
		EasyMock.expect(oldTrack2.getFilename()).andStubReturn(trackPath2.getFileName().toString());
		EasyMock.expect(oldTrack3.getTrackPath()).andStubReturn(trackPath3);
		EasyMock.expect(oldTrack3.getFilename()).andStubReturn(trackPath3.getFileName().toString());

		TrackStore mockTrackStore = this.createMock(TrackStore.class);

		EasyMock.expect(mockTrackStoreManager.getTrackStore(mockMusicPackProject)).andReturn(mockTrackStore).once();
		
		Collection<String> storedTracks = new ArrayList<>();

		EasyMock.expect(mockTrackStore.getStoredTracks()).andReturn(storedTracks).atLeastOnce();

		mockTrackStore.refresh();
		EasyMock.expectLastCall().once();

		EasyMock.expect(mockFileManager.exists(trackPath1)).andStubReturn(true);
		EasyMock.expect(mockFileManager.exists(trackPath2)).andStubReturn(false);
		EasyMock.expect(mockFileManager.exists(trackPath3)).andStubReturn(true);

		InputStream mockInputStream1 = this.createMock(InputStream.class);
		InputStream mockInputStream3 = this.createMock(InputStream.class);

		EasyMock.expect(mockFileManager.newInputStream(trackPath1)).andReturn(mockInputStream1).once();
		EasyMock.expect(mockFileManager.newInputStream(trackPath3)).andReturn(mockInputStream3).once();

		mockInputStream1.close();
		EasyMock.expectLastCall().once();

		mockInputStream3.close();
		EasyMock.expectLastCall().once();

		OutputStream mockOutputStream1 = this.createMock(OutputStream.class);
		OutputStream mockOutputStream3 = this.createMock(OutputStream.class);

		EasyMock.expect(mockTrackStore.openOutputStream("path1.ogg")).andReturn(mockOutputStream1).once();
		EasyMock.expect(mockTrackStore.openOutputStream("path3.ogg")).andReturn(mockOutputStream3).once();

		mockOutputStream1.close();
		EasyMock.expectLastCall().once();

		mockOutputStream3.close();
		EasyMock.expectLastCall().once();

		PowerMock.mockStatic(Utils.class);

		Utils.writeFromInputStreamToOutputStream(mockInputStream1, mockOutputStream1);
		EasyMock.expectLastCall().once();

		Utils.writeFromInputStreamToOutputStream(mockInputStream3, mockOutputStream3);
		EasyMock.expectLastCall().once();

		this.replayAll();
		PowerMock.replayAll();

		oldProjects.put(projectFolder, Arrays.asList(oldTrack1, oldTrack2, oldTrack3));

		compatibilityManager.applyPostLoadFixes(projectFolder, mockMusicPackProject, null);

		Assert.assertTrue(oldProjects.isEmpty());

		this.verifyAll();
		PowerMock.verifyAll();
	}

	@Test
	public void testApplyPostLoadFixesCopyTracksInMapException() throws IOException {
		Path projectFolder = Paths.get("project1");
		MusicPackProject mockMusicPackProject = createMockProject();

		craftedMods.lotrTools.musicPackCreator.data.Track oldTrack1 = this
				.createMock(craftedMods.lotrTools.musicPackCreator.data.Track.class);
		craftedMods.lotrTools.musicPackCreator.data.Track oldTrack2 = this
				.createMock(craftedMods.lotrTools.musicPackCreator.data.Track.class);
		craftedMods.lotrTools.musicPackCreator.data.Track oldTrack3 = this
				.createMock(craftedMods.lotrTools.musicPackCreator.data.Track.class);

		RuntimeException exception = new RuntimeException("Error");

		EasyMock.expect(mockTrackStoreManager.getTrackStore(mockMusicPackProject)).andThrow(exception).once();

		Capture<WriteableEventProperties> propertiesCapture = Capture.newInstance();

		EasyMock.expect(mockEventManager.dispatchEvent(
				EasyMock.eq(MusicPackProjectCompatibilityManager.POST_LOAD_SERIALIZED_WORKSPACE_TRACK_COPY_ERROR_EVENT),
				EasyMock.capture(propertiesCapture))).andReturn(null).once();

		this.replayAll();
		PowerMock.replayAll();

		oldProjects.put(projectFolder, Arrays.asList(oldTrack1, oldTrack2, oldTrack3));

		compatibilityManager.applyPostLoadFixes(projectFolder, mockMusicPackProject, null);

		Assert.assertTrue(oldProjects.isEmpty());

		WriteableEventProperties properties = propertiesCapture.getValue();

		Assert.assertEquals(mockMusicPackProject, properties.getProperty(
				MusicPackProjectCompatibilityManager.POST_LOAD_SERIALIZED_WORKSPACE_TRACK_COPY_ERROR_EVENT_MUSIC_PACK_PROJECT));
		Assert.assertEquals(exception, properties.getProperty(
				MusicPackProjectCompatibilityManager.POST_LOAD_SERIALIZED_WORKSPACE_TRACK_COPY_ERROR_EVENT_EXCEPTION));

		this.verifyAll();
		PowerMock.verifyAll();
	}

	private MusicPackProject createMockProject() {
		MusicPackProject mockMusicPackProject = this.createMock(MusicPackProject.class);
		MusicPack mockMusicPack = this.createMock(MusicPack.class);
		List<Track> tracks = new ArrayList<>();
		EasyMock.expect(mockMusicPackProject.getName()).andStubReturn("proj");
		EasyMock.expect(mockMusicPackProject.getMusicPack()).andStubReturn(mockMusicPack);
		EasyMock.expect(mockMusicPack.getTracks()).andStubReturn(tracks);
		return mockMusicPackProject;
	}

}
