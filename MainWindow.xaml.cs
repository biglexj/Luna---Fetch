using System;
using System.Collections.Generic;
using System.IO;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;

namespace LunaYtdlp
{
    public partial class MainWindow : Window
    {
        private readonly YtdlpManager _ytdlpManager;
        private VideoInfo? _currentVideoInfo;

        public MainWindow()
        {
            InitializeComponent();
            _ytdlpManager = new YtdlpManager();
            
            // Set default download folder to user's Downloads folder
            string downloadsFolder = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), 
                "Downloads"
            );
            FolderTextBox.Text = downloadsFolder;

            // Set up YtdlpManager event listeners
            _ytdlpManager.ProgressChanged += YtdlpManager_ProgressChanged;
            _ytdlpManager.OutputLogReceived += YtdlpManager_OutputLogReceived;

            // Detect and apply system theme on startup
            bool isLightTheme = IsWindowsLightTheme();
            ThemeToggleButton.IsChecked = isLightTheme;
            ApplyTheme(isLightTheme ? "Themes/LightTheme.xaml" : "Themes/DarkTheme.xaml");
        }

        // ================= THEME TOGGLING =================
        private void ThemeToggleButton_Checked(object sender, RoutedEventArgs e)
        {
            ApplyTheme("Themes/LightTheme.xaml");
        }

        private void ThemeToggleButton_Unchecked(object sender, RoutedEventArgs e)
        {
            ApplyTheme("Themes/DarkTheme.xaml");
        }

        private bool IsWindowsLightTheme()
        {
            try
            {
                using (var key = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize"))
                {
                    var value = key?.GetValue("AppsUseLightTheme");
                    if (value != null)
                    {
                        return (int)value == 1;
                    }
                }
            }
            catch
            {
                // Fallback to dark mode
            }
            return false;
        }

        private void ApplyTheme(string themePath)
        {
            try
            {
                var dict = new ResourceDictionary
                {
                    Source = new Uri(themePath, UriKind.Relative)
                };
                
                // Clear existing and add new
                Application.Current.Resources.MergedDictionaries.Clear();
                Application.Current.Resources.MergedDictionaries.Add(dict);
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Error al cambiar el tema: {ex.Message}", "Error", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        // ================= ANALYZE URL =================
        private async void AnalyzeButton_Click(object sender, RoutedEventArgs e)
        {
            string url = UrlTextBox.Text.Trim();
            if (string.IsNullOrEmpty(url))
            {
                StatusLabel.Text = "Por favor, ingresa un enlace válido.";
                return;
            }

            // Simple URL pattern check
            if (!url.StartsWith("http://", StringComparison.OrdinalIgnoreCase) && 
                !url.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                StatusLabel.Text = "El enlace debe comenzar con http:// o https://";
                return;
            }

            SetUiStateAnalyzing(true);
            LogTextBox.Clear();
            LogTextBox.Text += $"[INFO] Analizando enlace: {url}\n";

            try
            {
                _currentVideoInfo = await _ytdlpManager.GetVideoInfoAsync(url);
                DisplayVideoInfo(_currentVideoInfo);
                StatusLabel.Text = "Enlace analizado con éxito. Selecciona un formato y descarga.";
            }
            catch (Exception ex)
            {
                StatusLabel.Text = "Error al analizar el video.";
                MessageBox.Show($"No se pudo obtener información del video.\n\nDetalles:\n{ex.Message}", "Error", MessageBoxButton.OK, MessageBoxImage.Error);
                SetUiStateAnalyzing(false);
            }
        }

        private void SetUiStateAnalyzing(bool isAnalyzing)
        {
            if (isAnalyzing)
            {
                AnalyzeButton.IsEnabled = false;
                UrlTextBox.IsEnabled = false;
                StatusLabel.Text = "Analizando video... Por favor, espera.";
                VideoCard.Visibility = Visibility.Collapsed;
                DownloadButton.Visibility = Visibility.Collapsed;
                ProgressPanel.Visibility = Visibility.Collapsed;
            }
            else
            {
                AnalyzeButton.IsEnabled = true;
                UrlTextBox.IsEnabled = true;
            }
        }

        private void DisplayVideoInfo(VideoInfo info)
        {
            // Populate basic info
            VideoTitleLabel.Text = info.Title;
            VideoUploaderLabel.Text = info.Uploader;
            
            // Format Duration
            TimeSpan duration = TimeSpan.FromSeconds(info.Duration);
            VideoDurationLabel.Text = duration.TotalHours >= 1 
                ? $"Duración: {duration:hh\\:mm\\:ss}" 
                : $"Duración: {duration:mm\\:ss}";

            // Load Thumbnail
            if (!string.IsNullOrEmpty(info.ThumbnailUrl))
            {
                try
                {
                    ThumbnailBrush.ImageSource = new BitmapImage(new Uri(info.ThumbnailUrl));
                }
                catch
                {
                    // Fallback to empty if load fails
                    ThumbnailBrush.ImageSource = null;
                }
            }
            else
            {
                ThumbnailBrush.ImageSource = null;
            }

            // Populate File Types dropdown
            FileTypeComboBox.SelectionChanged -= FileTypeComboBox_SelectionChanged; // Detach temporarily to prevent double trigger
            FileTypeComboBox.Items.Clear();
            FileTypeComboBox.Items.Add(new FileTypeItem("Video (.mp4)", "mp4", false));
            FileTypeComboBox.Items.Add(new FileTypeItem("Video (.webm)", "webm", false));
            FileTypeComboBox.Items.Add(new FileTypeItem("Audio (.mp3)", "mp3", true));
            FileTypeComboBox.Items.Add(new FileTypeItem("Audio (.m4a)", "m4a", true));
            FileTypeComboBox.Items.Add(new FileTypeItem("Audio (.flac)", "flac", true));
            
            FileTypeComboBox.SelectionChanged += FileTypeComboBox_SelectionChanged;
            FileTypeComboBox.SelectedIndex = 0; // Trigger selection changed manually

            // Make card and button visible
            VideoCard.Visibility = Visibility.Visible;
            DownloadButton.Visibility = Visibility.Visible;

            // Re-enable input fields
            AnalyzeButton.IsEnabled = true;
            UrlTextBox.IsEnabled = true;
        }

        private void FileTypeComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (_currentVideoInfo == null) return;
            
            var selectedType = FileTypeComboBox.SelectedItem as FileTypeItem;
            if (selectedType == null) return;

            QualityComboBox.Items.Clear();

            if (selectedType.IsAudio)
            {
                // Audio Quality Options
                QualityLabel.Text = "Calidad:";
                QualityComboBox.Items.Add(new QualityItem("Mejor Calidad (320 kbps)", "bestaudio/best", "0")); // 0 is best in lame
                QualityComboBox.Items.Add(new QualityItem("Calidad Estándar (192 kbps)", "bestaudio/best", "5")); // 5 is standard
                QualityComboBox.Items.Add(new QualityItem("Calidad Ligera (128 kbps)", "bestaudio/best", "9")); // 9 is light
            }
            else
            {
                // Video Resolution Options based on max height
                QualityLabel.Text = "Calidad:";
                if (_currentVideoInfo.MaxHeight >= 1080)
                {
                    QualityComboBox.Items.Add(new QualityItem("1080p (Full HD)", "bestvideo[height<=1080]+bestaudio/best"));
                }
                if (_currentVideoInfo.MaxHeight >= 720)
                {
                    QualityComboBox.Items.Add(new QualityItem("720p (HD)", "bestvideo[height<=720]+bestaudio/best"));
                }
                if (_currentVideoInfo.MaxHeight >= 480)
                {
                    QualityComboBox.Items.Add(new QualityItem("480p (SD)", "bestvideo[height<=480]+bestaudio/best"));
                }
                if (_currentVideoInfo.MaxHeight >= 360 || QualityComboBox.Items.Count == 0)
                {
                    QualityComboBox.Items.Add(new QualityItem("360p (SD)", "bestvideo[height<=360]+bestaudio/best"));
                }
            }

            QualityComboBox.SelectedIndex = 0;
        }

        // Helper classes for ComboBox items
        private class FileTypeItem
        {
            public string DisplayName { get; }
            public string FormatExtension { get; }
            public bool IsAudio { get; }

            public FileTypeItem(string displayName, string formatExtension, bool isAudio)
            {
                DisplayName = displayName;
                FormatExtension = formatExtension;
                IsAudio = isAudio;
            }

            public override string ToString() => DisplayName;
        }

        private class QualityItem
        {
            public string DisplayName { get; }
            public string FormatCode { get; }
            public string AudioBitrate { get; }

            public QualityItem(string displayName, string formatCode, string audioBitrate = "")
            {
                DisplayName = displayName;
                FormatCode = formatCode;
                AudioBitrate = audioBitrate;
            }

            public override string ToString() => DisplayName;
        }

        // ================= BROWSE FOLDER =================
        private void BrowseFolderButton_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new Microsoft.Win32.OpenFolderDialog
            {
                InitialDirectory = FolderTextBox.Text,
                Title = "Selecciona la carpeta de destino"
            };

            if (dialog.ShowDialog() == true)
            {
                FolderTextBox.Text = dialog.FolderName;
            }
        }

        // ================= DOWNLOAD =================
        private async void DownloadButton_Click(object sender, RoutedEventArgs e)
        {
            if (_currentVideoInfo == null) return;

            var selectedType = FileTypeComboBox.SelectedItem as FileTypeItem;
            var selectedQuality = QualityComboBox.SelectedItem as QualityItem;
            
            if (selectedType == null || selectedQuality == null)
            {
                MessageBox.Show("Por favor, selecciona un tipo de archivo y calidad.", "Error", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            string outDir = FolderTextBox.Text.Trim();
            if (string.IsNullOrEmpty(outDir))
            {
                MessageBox.Show("Por favor, selecciona una carpeta de destino válida.", "Error", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            SetUiStateDownloading(true);
            LogTextBox.Clear();
            LogTextBox.Text += $"[INFO] Iniciando descarga de: {_currentVideoInfo.Title}\n";
            LogTextBox.Text += $"[INFO] Formato: {selectedType.DisplayName} - {selectedQuality.DisplayName}\n";
            LogTextBox.Text += $"[INFO] Carpeta de destino: {outDir}\n\n";

            try
            {
                await _ytdlpManager.DownloadVideoAsync(
                    _currentVideoInfo.Url, 
                    selectedQuality.FormatCode, 
                    outDir, 
                    selectedType.FormatExtension, 
                    selectedQuality.AudioBitrate
                );
                
                ProgressStatusText.Text = "¡Descarga completada!";
                DownloadProgressBar.Value = 100;
                ProgressPercentText.Text = "100.0%";
                StatusLabel.Text = "Descarga finalizada con éxito.";
                MessageBox.Show("La descarga ha finalizado correctamente.", "Descarga Completada", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            catch (Exception ex)
            {
                ProgressStatusText.Text = "Error al descargar.";
                StatusLabel.Text = "Ocurrió un error en la descarga.";
                MessageBox.Show($"La descarga falló.\n\nDetalles:\n{ex.Message}", "Error de Descarga", MessageBoxButton.OK, MessageBoxImage.Error);
            }
            finally
            {
                SetUiStateDownloading(false);
            }
        }

        private void SetUiStateDownloading(bool isDownloading)
        {
            if (isDownloading)
            {
                AnalyzeButton.IsEnabled = false;
                UrlTextBox.IsEnabled = false;
                DownloadButton.IsEnabled = false;
                FileTypeComboBox.IsEnabled = false;
                QualityComboBox.IsEnabled = false;
                BrowseFolderButton.IsEnabled = false;
                
                // Show Progress panel
                ProgressPanel.Visibility = Visibility.Visible;
                DownloadProgressBar.Value = 0;
                ProgressPercentText.Text = "0.0%";
                DownloadSpeedText.Text = "Velocidad: Esperando...";
                DownloadSizeText.Text = "Tamaño: --";
                DownloadEtaText.Text = "ETA: --";
                ProgressStatusText.Text = "Iniciando descarga...";
            }
            else
            {
                AnalyzeButton.IsEnabled = true;
                UrlTextBox.IsEnabled = true;
                DownloadButton.IsEnabled = true;
                FileTypeComboBox.IsEnabled = true;
                QualityComboBox.IsEnabled = true;
                BrowseFolderButton.IsEnabled = true;
            }
        }

        // ================= PROGRESS EVENTS =================
        private void YtdlpManager_ProgressChanged(object? sender, DownloadProgressEventArgs e)
        {
            // Ensure UI updates happen on main dispatcher thread
            Dispatcher.Invoke(() =>
            {
                DownloadProgressBar.Value = e.Percentage;
                ProgressPercentText.Text = $"{e.Percentage:0.0}%";
                ProgressStatusText.Text = e.StatusMessage;

                if (!string.IsNullOrEmpty(e.Speed))
                    DownloadSpeedText.Text = $"Velocidad: {e.Speed}";
                
                if (!string.IsNullOrEmpty(e.Size))
                    DownloadSizeText.Text = $"Tamaño: {e.Size}";

                if (!string.IsNullOrEmpty(e.Eta))
                    DownloadEtaText.Text = $"ETA: {e.Eta}";
            });
        }

        private void YtdlpManager_OutputLogReceived(object? sender, string logLine)
        {
            Dispatcher.Invoke(() =>
            {
                LogTextBox.AppendText(logLine + "\n");
                LogTextBox.ScrollToEnd();
            });
        }

        private void UrlTextBox_GotFocus(object sender, RoutedEventArgs e)
        {
            // Auto-select text on focus to make pasting easier
            UrlTextBox.SelectAll();
        }
    }
}