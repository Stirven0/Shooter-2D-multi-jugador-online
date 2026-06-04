package com.aa.client.asset;

import javafx.scene.media.AudioClip;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestor de audio del cliente.
 * Proporciona métodos estáticos para reproducir efectos de sonido (SFX)
 * y música de fondo, con control de volumen maestro y por canal.
 * Usa AudioClip para todo (WAV nativo, sin dependencia de GStreamer).
 */
public class AudioManager {
    private static final Map<String, AudioClip> sfxCache = new HashMap<>();
    private static AudioClip currentMusic;
    private static double masterVolume = 0.7;
    private static double sfxVolume = 0.8;
    private static double musicVolume = 0.5;

    /**
     * Reproduce el sonido de disparo.
     */
    public static void playShoot() {
        playSfx("sfx/shoot_pistol.wav");
    }

    /**
     * Reproduce el sonido de impacto al acertar a un objetivo.
     */
    public static void playHit() {
        playSfx("sfx/hit_marker.wav");
    }

    /**
     * Reproduce el sonido de explosión.
     */
    public static void playExplosion() {
        playSfx("sfx/explosion.wav");
    }

    /**
     * Reproduce el sonido de clic de interfaz con volumen reducido.
     */
    public static void playClick() {
        playSfx("ui/click.wav", 0.3);
    }

    private static void playSfx(String path) {
        playSfx(path, sfxVolume);
    }

    /**
     * Carga y reproduce un efecto de sonido con el volumen especificado.
     * Los clips se cachean para evitar recargas.
     * @param path ruta relativa del recurso de audio dentro de /audio/
     * @param vol factor de volumen adicional (0..1)
     */
    private static void playSfx(String path, double vol) {
        try {
            AudioClip clip = sfxCache.computeIfAbsent(path, p -> {
                var url = AudioManager.class.getResource("/audio/" + p);
                if (url == null) return null;
                try {
                    return new AudioClip(url.toExternalForm());
                } catch (Exception e) {
                    System.err.println("[AUDIO] Error loading SFX " + p + ": " + e.getMessage());
                    return null;
                }
            });
            if (clip != null) {
                clip.play(masterVolume * vol);
            }
        } catch (Exception e) {
            System.err.println("[AUDIO] Error playing SFX: " + e.getMessage());
        }
    }

    /**
     * Reproduce música de fondo en bucle infinito usando AudioClip.
     * Detiene cualquier música que se estuviera reproduciendo previamente.
     * @param path ruta del archivo de música dentro de /audio/
     */
    public static void playMusic(String path) {
        try {
            stopMusic();
            var url = AudioManager.class.getResource("/audio/" + path);
            if (url == null) {
                System.err.println("[AUDIO] Music file not found: " + path);
                return;
            }
            AudioClip clip = sfxCache.computeIfAbsent(path, p -> {
                try {
                    return new AudioClip(url.toExternalForm());
                } catch (Exception e) {
                    System.err.println("[AUDIO] Error loading music " + p + ": " + e.getMessage());
                    return null;
                }
            });
            if (clip != null) {
                currentMusic = clip;
                clip.setCycleCount(AudioClip.INDEFINITE);
                clip.play(masterVolume * musicVolume);
            }
        } catch (Exception e) {
            System.err.println("[AUDIO] Error playing music: " + e.getMessage());
        }
    }

    /** Detiene y libera la música de fondo actual. */
    public static void stopMusic() {
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic = null;
        }
    }

    /** @return volumen maestro actual (0..1) */
    public static double getMasterVolume() { return masterVolume; }
    /** @return volumen de efectos actual (0..1) */
    public static double getSfxVolume() { return sfxVolume; }
    /** @return volumen de música actual (0..1) */
    public static double getMusicVolume() { return musicVolume; }

    /**
     * Establece el volumen maestro.
     * @param v nuevo volumen maestro (0..1)
     */
    public static void setMasterVolume(double v) { masterVolume = v; updateMusicVolume(); }

    /**
     * Establece el volumen de efectos.
     * @param v nuevo volumen de efectos (0..1)
     */
    public static void setSfxVolume(double v) { sfxVolume = v; }

    /**
     * Establece el volumen de música.
     * @param v nuevo volumen de música (0..1)
     */
    public static void setMusicVolume(double v) { musicVolume = v; updateMusicVolume(); }

    /** Actualiza el volumen de la música activa. */
    private static void updateMusicVolume() {
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic.play(masterVolume * musicVolume);
        }
    }
}