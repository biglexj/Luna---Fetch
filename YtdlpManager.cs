using System;
using System.Diagnostics;
using System.IO;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace LunaYtdlp
{
    public class VideoInfo
    {
        public string Title { get; set; } = "Unknown Title";
        public string Uploader { get; set; } = "Unknown Uploader";
        public double Duration { get; set; } // in seconds
        public string ThumbnailUrl { get; set; } = "";
        public int MaxHeight { get; set; }
        public string Url { get; set; } = "";
    }

    public class DownloadProgressEventArgs : EventArgs
    {
        public double Percentage { get; set; }
        public string Speed { get; set; } = "";
        public string Eta { get; set; } = "";
        public string Size { get; set; } = "";
        public string StatusMessage { get; set; } = "";
    }

    public class YtdlpManager
    {
        // Regex for parsing yt-dlp progress
        // Example: [download]  12.3% of ~50.20MiB at  3.45MiB/s ETA 00:12
        private static readonly Regex ProgressRegex = new Regex(
            @"\[download\]\s+(?<pct>\d+(?:\.\d+)?)%\s+of\s+(?:~)?(?<size>\d+(?:\.\d+)?\w+)\s+at\s+(?<speed>\d+(?:\.\d+)?\w+/s)\s+ETA\s+(?<eta>\d{2}:\d{2}(?::\d{2})?)",
            RegexOptions.Compiled | RegexOptions.IgnoreCase);

        // Regex for FFmpeg merge or other post-processing steps
        private static readonly Regex PostProcessRegex = new Regex(
            @"\[(?:Merger|ExtractAudio|VideoConvertor)\]",
            RegexOptions.Compiled | RegexOptions.IgnoreCase);

        public event EventHandler<DownloadProgressEventArgs>? ProgressChanged;
        public event EventHandler<string>? OutputLogReceived;

        /// <summary>
        /// Fetches video details from a URL using yt-dlp --dump-json
        /// </summary>
        public async Task<VideoInfo> GetVideoInfoAsync(string url)
        {
            var psi = new ProcessStartInfo
            {
                FileName = "yt-dlp",
                Arguments = $"--dump-json --no-playlist \"{url}\"",
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
                EnvironmentVariables = { ["PAGER"] = "cat" }
            };

            using var process = new Process { StartInfo = psi };
            
            string output = "";
            string error = "";

            process.OutputDataReceived += (s, e) => { if (e.Data != null) output += e.Data + "\n"; };
            process.ErrorDataReceived += (s, e) => { if (e.Data != null) error += e.Data + "\n"; };

            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();

            await process.WaitForExitAsync();

            if (process.ExitCode != 0)
            {
                throw new Exception($"yt-dlp failed with exit code {process.ExitCode}. Error: {error}");
            }

            try
            {
                using var doc = JsonDocument.Parse(output);
                var root = doc.RootElement;

                var info = new VideoInfo
                {
                    Url = url,
                    Title = root.TryGetProperty("title", out var titleProp) ? titleProp.GetString() ?? "Unknown Title" : "Unknown Title",
                    Uploader = root.TryGetProperty("uploader", out var uploaderProp) ? uploaderProp.GetString() ?? "Unknown Uploader" : "Unknown Uploader",
                    Duration = root.TryGetProperty("duration", out var durProp) ? durProp.GetDouble() : 0.0,
                    ThumbnailUrl = root.TryGetProperty("thumbnail", out var thumbProp) ? thumbProp.GetString() ?? "" : "",
                    MaxHeight = root.TryGetProperty("height", out var hProp) && hProp.ValueKind == JsonValueKind.Number ? hProp.GetInt32() : 1080
                };

                return info;
            }
            catch (Exception ex)
            {
                throw new Exception("Failed to parse yt-dlp metadata JSON. Details: " + ex.Message, ex);
            }
        }

        /// <summary>
        /// Downloads the video to a specific directory with chosen format
        /// </summary>
        public async Task DownloadVideoAsync(string url, string formatSelection, string outputDirectory, string containerFormat, string audioBitrate = "")
        {
            if (!Directory.Exists(outputDirectory))
            {
                Directory.CreateDirectory(outputDirectory);
            }

            // yt-dlp output template: %(title)s.%(ext)s
            string outputTemplate = Path.Combine(outputDirectory, "%(title)s.%(ext)s");
            
            // Build arguments
            // --newline prints progress line-by-line suitable for parsing
            // --progress prints progress to stdout
            string extraArgs = "";
            if (containerFormat == "mp3" || containerFormat == "m4a" || containerFormat == "flac")
            {
                extraArgs = $" -x --audio-format {containerFormat}";
                if (containerFormat == "mp3" && !string.IsNullOrEmpty(audioBitrate))
                {
                    extraArgs += $" --audio-quality {audioBitrate}";
                }
            }
            else // Video (mp4 or webm)
            {
                extraArgs = $" --merge-output-format {containerFormat}";
            }

            string arguments = $"--newline --progress -f \"{formatSelection}\"{extraArgs} -o \"{outputTemplate}\" --no-playlist \"{url}\"";

            var psi = new ProcessStartInfo
            {
                FileName = "yt-dlp",
                Arguments = arguments,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
                EnvironmentVariables = { ["PAGER"] = "cat" }
            };

            using var process = new Process { StartInfo = psi };

            process.OutputDataReceived += (s, e) =>
            {
                if (e.Data == null) return;
                
                // Raise log event
                OutputLogReceived?.Invoke(this, e.Data);

                // Parse progress
                ParseAndReportProgress(e.Data);
            };

            process.ErrorDataReceived += (s, e) =>
            {
                if (e.Data == null) return;
                OutputLogReceived?.Invoke(this, "[ERROR] " + e.Data);
            };

            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();

            await process.WaitForExitAsync();

            if (process.ExitCode != 0)
            {
                throw new Exception($"Download failed with exit code {process.ExitCode}. See log for details.");
            }
        }

        private void ParseAndReportProgress(string line)
        {
            try
            {
                if (ProgressRegex.IsMatch(line))
                {
                    var match = ProgressRegex.Match(line);
                    double pct = double.Parse(match.Groups["pct"].Value, System.Globalization.CultureInfo.InvariantCulture);
                    string size = match.Groups["size"].Value;
                    string speed = match.Groups["speed"].Value;
                    string eta = match.Groups["eta"].Value;

                    ProgressChanged?.Invoke(this, new DownloadProgressEventArgs
                    {
                        Percentage = pct,
                        Size = size,
                        Speed = speed,
                        Eta = eta,
                        StatusMessage = $"Descargando... {pct:0.0}%"
                    });
                }
                else if (PostProcessRegex.IsMatch(line))
                {
                    ProgressChanged?.Invoke(this, new DownloadProgressEventArgs
                    {
                        Percentage = 100.0,
                        StatusMessage = "Procesando y combinando audio/video con FFmpeg..."
                    });
                }
                else if (line.Contains("[download] 100%"))
                {
                    ProgressChanged?.Invoke(this, new DownloadProgressEventArgs
                    {
                        Percentage = 100.0,
                        StatusMessage = "Descarga completada. Finalizando..."
                    });
                }
            }
            catch
            {
                // Ignore parsing errors for individual lines to prevent crashing the download process
            }
        }
    }
}
