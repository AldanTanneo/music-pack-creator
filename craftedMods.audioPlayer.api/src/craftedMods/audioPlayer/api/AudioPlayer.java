package craftedMods.audioPlayer.api;

import java.io.InputStream;

import org.osgi.annotation.versioning.ProviderType;

import craftedMods.eventManager.api.EventDispatchPolicy;
import craftedMods.eventManager.api.EventInfo;
import craftedMods.eventManager.api.PropertyKey;
import craftedMods.eventManager.base.DefaultEventInfo;
import craftedMods.eventManager.base.DefaultPropertyKey;

@ProviderType
public interface AudioPlayer {
	
	public static final String SUPPORTED_FORMATS_PROPERTY_KEY = "supportedFormats";

	public static final EventInfo PLAY_TRACK_ERROR_EVENT = new DefaultEventInfo(AudioPlayer.class, "PLAY_TRACK_ERROR",
			EventDispatchPolicy.NOT_SPECIFIED);

	public static final PropertyKey<String> PLAY_TRACK_ERROR_EVENT_NAME = DefaultPropertyKey.createStringPropertyKey();
	public static final PropertyKey<Exception> PLAY_TRACK_ERROR_EVENT_EXCEPTION = DefaultPropertyKey
			.createPropertyKey(Exception.class);

	public boolean play(InputStream track, String name);
	
	/**
	 * Returns the current playing track or null, if no track is playing.
	 * @return The current playing track
	 */
	public String getCurrentTrack();

	public void pause();

	public void resume();

	public void stop();

	public long getCurrentPlayingTime();

	public long getMaxPlayingTime();

	/**
	 * Returns the volume, which is a value between 0 and 100.
	 * 
	 * @return The volume
	 */
	public int getVolume();

	/**
	 * Sets the volume. The supplied value will be capped between 0 and 100.
	 * 
	 * @param volume The volume
	 */
	public void setVolume(int volume);

	/**
	 * Returns whether a track is playing is is paused. If the player was stopped,
	 * it returns false.
	 * 
	 * @return Whether a track is active
	 */
	public boolean isTrackActive();

	public boolean isPaused();

}
