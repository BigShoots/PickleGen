using System;
using System.Collections.Generic;
using System.Drawing;
using System.Globalization;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using PickleCalLG.Meters;
using PickleCalLG.Meters.Argyll;
using PickleCalLG.Meters.Simulation;
using PickleCalLG.Meters.Sequences;

namespace PickleCalLG
{
    public partial class MainForm : Form
    {
        private LgTvController? _tvController;
        private PGenServer? _pgenServer;
        private string _tvIp = "";
        private MeterManager _meterManager;
        private readonly CancellationTokenSource _meterCancellation = new();
        private MeterMeasurementState _lastMeterState = MeterMeasurementState.Disconnected;
        private MeasurementQueueRunner? _sequenceRunner;
        private CancellationTokenSource? _sequenceCancellation;
        private bool _sequenceRunning;

        private TabControl tabControl1;
        private TabPage tabPageConnection;
        private TabPage tabPageTvControl;
        private TabPage tabPagePictureSettings;
        private TabPage tabPageWhiteBalance;
        private TabPage tabPagePatternGen;
        private TabPage tabPageMeter;
        private GroupBox groupBox1;
        private CheckBox chkSecureConnection;
        private TextBox txtTvIp;
        private Label label1;
        private Button btnDisconnect;
        private Button btnConnect;
        private Label lblStatus;
        private GroupBox groupBox2;
        private Button btnApplyPictureMode;
        private ComboBox cmbPictureMode;
        private Label label8;
        private GroupBox groupBox3;
        private Button btnApplyColorSettings;
        private ComboBox cmbColorTemp;
        private ComboBox cmbGamma;
        private ComboBox cmbColorGamut;
        private Label label11;
        private Label label10;
        private Label label9;
        private GroupBox groupBox4;
        private Button btnDisableProcessing;
        private Button btnReadSettings;
        private GroupBox groupBoxMeterSelect;
        private GroupBox groupBoxMeterControl;
        private ComboBox cmbMeters;
        private Button btnMeterRefresh;
        private Button btnMeterConnect;
        private Button btnMeterDisconnect;
        private Button btnMeterCalibrate;
        private Button btnMeterMeasure;
        private CheckBox chkMeterAveraging;
        private CheckBox chkMeterHighRes;
        private TextBox txtMeterDisplayType;
        private Label lblMeterStatus;
        private Label lblMeterMeasurement;
        private Label lblMeterDisplayType;
        private GroupBox groupBoxMeasurementSequence;
        private Button btnRunGrayscale;
        private Button btnRunColorSweep;
        private Button btnStopSequence;
        private ListBox lstMeasurementLog;
        private Label lblSequenceStatus;

        public MainForm()
        {
            InitializeComponent();
            InitializeUI();
            _meterManager = new MeterManager(
                new CompositeMeterDiscoveryService(
                    new ArgyllSpotreadDiscoveryService(),
                    new SimulatedMeterDiscoveryService()));
            _meterManager.MeterStateChanged += MeterManagerOnStateChanged;
            _meterManager.MeasurementAvailable += MeterManagerOnMeasurement;
            Load += MainForm_LoadAsync;
            FormClosing += MainForm_FormClosing;
        }

        private void InitializeComponent()
        {
            tabControl1 = new TabControl();
            tabPageConnection = new TabPage();
            tabPageTvControl = new TabPage();
            tabPagePictureSettings = new TabPage();
            tabPageWhiteBalance = new TabPage();
            tabPagePatternGen = new TabPage();
            tabPageMeter = new TabPage();
            groupBox1 = new GroupBox();
            chkSecureConnection = new CheckBox();
            txtTvIp = new TextBox();
            label1 = new Label();
            btnDisconnect = new Button();
            btnConnect = new Button();
            lblStatus = new Label();
            groupBox2 = new GroupBox();
            btnApplyPictureMode = new Button();
            cmbPictureMode = new ComboBox();
            label8 = new Label();
            groupBox3 = new GroupBox();
            btnApplyColorSettings = new Button();
            cmbColorTemp = new ComboBox();
            cmbGamma = new ComboBox();
            cmbColorGamut = new ComboBox();
            label11 = new Label();
            label10 = new Label();
            label9 = new Label();
            groupBox4 = new GroupBox();
            btnDisableProcessing = new Button();
            btnReadSettings = new Button();
            groupBoxMeterSelect = new GroupBox();
            groupBoxMeterControl = new GroupBox();
            groupBoxMeasurementSequence = new GroupBox();
            cmbMeters = new ComboBox();
            btnMeterRefresh = new Button();
            btnMeterConnect = new Button();
            btnMeterDisconnect = new Button();
            btnMeterCalibrate = new Button();
            btnMeterMeasure = new Button();
            chkMeterAveraging = new CheckBox();
            chkMeterHighRes = new CheckBox();
            txtMeterDisplayType = new TextBox();
            lblMeterStatus = new Label();
            lblMeterMeasurement = new Label();
            lblMeterDisplayType = new Label();
            btnRunGrayscale = new Button();
            btnRunColorSweep = new Button();
            btnStopSequence = new Button();
            lstMeasurementLog = new ListBox();
            lblSequenceStatus = new Label();

            tabControl1.SuspendLayout();
            tabPageConnection.SuspendLayout();
            groupBox1.SuspendLayout();
            tabPageTvControl.SuspendLayout();
            groupBox2.SuspendLayout();
            tabPagePictureSettings.SuspendLayout();
            groupBox3.SuspendLayout();
            tabPageWhiteBalance.SuspendLayout();
            tabPageMeter.SuspendLayout();
            groupBoxMeterSelect.SuspendLayout();
            groupBoxMeterControl.SuspendLayout();
            groupBoxMeasurementSequence.SuspendLayout();
            SuspendLayout();

            // tabControl1
            tabControl1.Controls.Add(tabPageConnection);
            tabControl1.Controls.Add(tabPageTvControl);
            tabControl1.Controls.Add(tabPagePictureSettings);
            tabControl1.Controls.Add(tabPageWhiteBalance);
            tabControl1.Controls.Add(tabPagePatternGen);
            tabControl1.Controls.Add(tabPageMeter);
            tabControl1.Dock = DockStyle.Fill;
            tabControl1.Location = new Point(0, 0);
            tabControl1.Name = "tabControl1";
            tabControl1.SelectedIndex = 0;
            tabControl1.Size = new Size(800, 600);
            tabControl1.TabIndex = 0;

            // tabPageConnection
            tabPageConnection.Controls.Add(groupBox1);
            tabPageConnection.Controls.Add(lblStatus);
            tabPageConnection.Location = new Point(4, 24);
            tabPageConnection.Name = "tabPageConnection";
            tabPageConnection.Padding = new Padding(3);
            tabPageConnection.Size = new Size(792, 572);
            tabPageConnection.TabIndex = 0;
            tabPageConnection.Text = "Connection";

            // groupBox1
            groupBox1.Controls.Add(chkSecureConnection);
            groupBox1.Controls.Add(txtTvIp);
            groupBox1.Controls.Add(label1);
            groupBox1.Controls.Add(btnDisconnect);
            groupBox1.Controls.Add(btnConnect);
            groupBox1.Location = new Point(20, 20);
            groupBox1.Name = "groupBox1";
            groupBox1.Size = new Size(350, 150);
            groupBox1.TabIndex = 0;
            groupBox1.TabStop = false;
            groupBox1.Text = "TV Connection";

            // chkSecureConnection
            chkSecureConnection.AutoSize = true;
            chkSecureConnection.Location = new Point(10, 70);
            chkSecureConnection.Name = "chkSecureConnection";
            chkSecureConnection.Size = new Size(100, 17);
            chkSecureConnection.TabIndex = 4;
            chkSecureConnection.Text = "Secure Connection";
            chkSecureConnection.UseVisualStyleBackColor = true;

            // txtTvIp
            txtTvIp.Location = new Point(100, 35);
            txtTvIp.Name = "txtTvIp";
            txtTvIp.Size = new Size(150, 20);
            txtTvIp.TabIndex = 3;
            txtTvIp.Text = "192.168.1.";

            // label1
            label1.AutoSize = true;
            label1.Location = new Point(10, 38);
            label1.Name = "label1";
            label1.Size = new Size(85, 13);
            label1.TabIndex = 2;
            label1.Text = "TV IP Address:";

            // btnDisconnect
            btnDisconnect.Enabled = false;
            btnDisconnect.Location = new Point(260, 100);
            btnDisconnect.Name = "btnDisconnect";
            btnDisconnect.Size = new Size(75, 25);
            btnDisconnect.TabIndex = 1;
            btnDisconnect.Text = "Disconnect";
            btnDisconnect.UseVisualStyleBackColor = true;
            btnDisconnect.Click += btnDisconnect_Click;

            // btnConnect
            btnConnect.Location = new Point(175, 100);
            btnConnect.Name = "btnConnect";
            btnConnect.Size = new Size(75, 25);
            btnConnect.TabIndex = 0;
            btnConnect.Text = "Connect";
            btnConnect.UseVisualStyleBackColor = true;
            btnConnect.Click += btnConnect_Click;

            // lblStatus
            lblStatus.AutoSize = true;
            lblStatus.Location = new Point(20, 180);
            lblStatus.Name = "lblStatus";
            lblStatus.Size = new Size(35, 13);
            lblStatus.TabIndex = 1;
            lblStatus.Text = "Ready";

            // tabPageTvControl
            tabPageTvControl.Controls.Add(groupBox2);
            tabPageTvControl.Location = new Point(4, 24);
            tabPageTvControl.Name = "tabPageTvControl";
            tabPageTvControl.Padding = new Padding(3);
            tabPageTvControl.Size = new Size(792, 572);
            tabPageTvControl.TabIndex = 1;
            tabPageTvControl.Text = "TV Control";

            // groupBox2
            groupBox2.Controls.Add(btnApplyPictureMode);
            groupBox2.Controls.Add(cmbPictureMode);
            groupBox2.Controls.Add(label8);
            groupBox2.Location = new Point(20, 20);
            groupBox2.Name = "groupBox2";
            groupBox2.Size = new Size(300, 100);
            groupBox2.TabIndex = 0;
            groupBox2.TabStop = false;
            groupBox2.Text = "Picture Mode";

            // btnApplyPictureMode
            btnApplyPictureMode.Location = new Point(215, 40);
            btnApplyPictureMode.Name = "btnApplyPictureMode";
            btnApplyPictureMode.Size = new Size(75, 25);
            btnApplyPictureMode.TabIndex = 2;
            btnApplyPictureMode.Text = "Apply";
            btnApplyPictureMode.UseVisualStyleBackColor = true;
            btnApplyPictureMode.Click += btnApplyPictureMode_Click;

            // cmbPictureMode
            cmbPictureMode.FormattingEnabled = true;
            cmbPictureMode.Location = new Point(10, 42);
            cmbPictureMode.Name = "cmbPictureMode";
            cmbPictureMode.Size = new Size(150, 21);
            cmbPictureMode.TabIndex = 1;

            // label8
            label8.AutoSize = true;
            label8.Location = new Point(10, 20);
            label8.Name = "label8";
            label8.Size = new Size(69, 13);
            label8.TabIndex = 0;
            label8.Text = "Picture Mode:";

            // tabPagePictureSettings
            tabPagePictureSettings.Controls.Add(groupBox3);
            tabPagePictureSettings.Location = new Point(4, 24);
            tabPagePictureSettings.Name = "tabPagePictureSettings";
            tabPagePictureSettings.Padding = new Padding(3);
            tabPagePictureSettings.Size = new Size(792, 572);
            tabPagePictureSettings.TabIndex = 2;
            tabPagePictureSettings.Text = "Picture Settings";

            // groupBox3
            groupBox3.Controls.Add(btnApplyColorSettings);
            groupBox3.Controls.Add(cmbColorTemp);
            groupBox3.Controls.Add(cmbGamma);
            groupBox3.Controls.Add(cmbColorGamut);
            groupBox3.Controls.Add(label11);
            groupBox3.Controls.Add(label10);
            groupBox3.Controls.Add(label9);
            groupBox3.Location = new Point(20, 20);
            groupBox3.Name = "groupBox3";
            groupBox3.Size = new Size(350, 150);
            groupBox3.TabIndex = 0;
            groupBox3.TabStop = false;
            groupBox3.Text = "Color Settings";

            // btnApplyColorSettings
            btnApplyColorSettings.Location = new Point(260, 100);
            btnApplyColorSettings.Name = "btnApplyColorSettings";
            btnApplyColorSettings.Size = new Size(75, 25);
            btnApplyColorSettings.TabIndex = 6;
            btnApplyColorSettings.Text = "Apply";
            btnApplyColorSettings.UseVisualStyleBackColor = true;
            btnApplyColorSettings.Click += btnApplyColorSettings_Click;

            // cmbColorTemp
            cmbColorTemp.FormattingEnabled = true;
            cmbColorTemp.Location = new Point(100, 70);
            cmbColorTemp.Name = "cmbColorTemp";
            cmbColorTemp.Size = new Size(150, 21);
            cmbColorTemp.TabIndex = 5;

            // cmbGamma
            cmbGamma.FormattingEnabled = true;
            cmbGamma.Location = new Point(100, 45);
            cmbGamma.Name = "cmbGamma";
            cmbGamma.Size = new Size(150, 21);
            cmbGamma.TabIndex = 4;

            // cmbColorGamut
            cmbColorGamut.FormattingEnabled = true;
            cmbColorGamut.Location = new Point(100, 20);
            cmbColorGamut.Name = "cmbColorGamut";
            cmbColorGamut.Size = new Size(150, 21);
            cmbColorGamut.TabIndex = 3;

            // label11
            label11.AutoSize = true;
            label11.Location = new Point(10, 73);
            label11.Name = "label11";
            label11.Size = new Size(83, 13);
            label11.TabIndex = 2;
            label11.Text = "Color Temperature";

            // label10
            label10.AutoSize = true;
            label10.Location = new Point(10, 48);
            label10.Name = "label10";
            label10.Size = new Size(42, 13);
            label10.TabIndex = 1;
            label10.Text = "Gamma";

            // label9
            label9.AutoSize = true;
            label9.Location = new Point(10, 23);
            label9.Name = "label9";
            label9.Size = new Size(70, 13);
            label9.TabIndex = 0;
            label9.Text = "Color Gamut";

            // tabPageWhiteBalance
            tabPageWhiteBalance.Location = new Point(4, 24);
            tabPageWhiteBalance.Name = "tabPageWhiteBalance";
            tabPageWhiteBalance.Padding = new Padding(3);
            tabPageWhiteBalance.Size = new Size(792, 572);
            tabPageWhiteBalance.TabIndex = 3;
            tabPageWhiteBalance.Text = "White Balance";

            // tabPagePatternGen
            tabPagePatternGen.Location = new Point(4, 24);
            tabPagePatternGen.Name = "tabPagePatternGen";
            tabPagePatternGen.Padding = new Padding(3);
            tabPagePatternGen.Size = new Size(792, 572);
            tabPagePatternGen.TabIndex = 4;
            tabPagePatternGen.Text = "Pattern Gen";

            // tabPageMeter
            tabPageMeter.Controls.Add(groupBoxMeasurementSequence);
            tabPageMeter.Controls.Add(groupBoxMeterControl);
            tabPageMeter.Controls.Add(groupBoxMeterSelect);
            tabPageMeter.Controls.Add(lblMeterMeasurement);
            tabPageMeter.Controls.Add(lblMeterStatus);
            tabPageMeter.Location = new Point(4, 24);
            tabPageMeter.Name = "tabPageMeter";
            tabPageMeter.Padding = new Padding(3);
            tabPageMeter.Size = new Size(792, 572);
            tabPageMeter.TabIndex = 5;
            tabPageMeter.Text = "Meters";

            // groupBoxMeterSelect
            groupBoxMeterSelect.Controls.Add(btnMeterDisconnect);
            groupBoxMeterSelect.Controls.Add(btnMeterConnect);
            groupBoxMeterSelect.Controls.Add(btnMeterRefresh);
            groupBoxMeterSelect.Controls.Add(cmbMeters);
            groupBoxMeterSelect.Location = new Point(20, 20);
            groupBoxMeterSelect.Name = "groupBoxMeterSelect";
            groupBoxMeterSelect.Size = new Size(360, 130);
            groupBoxMeterSelect.TabIndex = 0;
            groupBoxMeterSelect.TabStop = false;
            groupBoxMeterSelect.Text = "Meter";

            // cmbMeters
            cmbMeters.DropDownStyle = ComboBoxStyle.DropDownList;
            cmbMeters.FormattingEnabled = true;
            cmbMeters.Location = new Point(15, 35);
            cmbMeters.Name = "cmbMeters";
            cmbMeters.Size = new Size(200, 23);
            cmbMeters.TabIndex = 0;

            // btnMeterRefresh
            btnMeterRefresh.Location = new Point(230, 34);
            btnMeterRefresh.Name = "btnMeterRefresh";
            btnMeterRefresh.Size = new Size(110, 25);
            btnMeterRefresh.TabIndex = 1;
            btnMeterRefresh.Text = "Refresh";
            btnMeterRefresh.UseVisualStyleBackColor = true;
            btnMeterRefresh.Click += btnMeterRefresh_Click;

            // btnMeterConnect
            btnMeterConnect.Location = new Point(15, 75);
            btnMeterConnect.Name = "btnMeterConnect";
            btnMeterConnect.Size = new Size(110, 25);
            btnMeterConnect.TabIndex = 2;
            btnMeterConnect.Text = "Connect";
            btnMeterConnect.UseVisualStyleBackColor = true;
            btnMeterConnect.Click += btnMeterConnect_Click;

            // btnMeterDisconnect
            btnMeterDisconnect.Location = new Point(230, 75);
            btnMeterDisconnect.Name = "btnMeterDisconnect";
            btnMeterDisconnect.Size = new Size(110, 25);
            btnMeterDisconnect.TabIndex = 3;
            btnMeterDisconnect.Text = "Disconnect";
            btnMeterDisconnect.UseVisualStyleBackColor = true;
            btnMeterDisconnect.Click += btnMeterDisconnect_Click;

            // groupBoxMeterControl
            groupBoxMeterControl.Controls.Add(btnMeterMeasure);
            groupBoxMeterControl.Controls.Add(btnMeterCalibrate);
            groupBoxMeterControl.Controls.Add(txtMeterDisplayType);
            groupBoxMeterControl.Controls.Add(lblMeterDisplayType);
            groupBoxMeterControl.Controls.Add(chkMeterHighRes);
            groupBoxMeterControl.Controls.Add(chkMeterAveraging);
            groupBoxMeterControl.Location = new Point(20, 170);
            groupBoxMeterControl.Name = "groupBoxMeterControl";
            groupBoxMeterControl.Size = new Size(360, 180);
            groupBoxMeterControl.TabIndex = 1;
            groupBoxMeterControl.TabStop = false;
            groupBoxMeterControl.Text = "Measurement";

            // groupBoxMeasurementSequence
            groupBoxMeasurementSequence.Controls.Add(lstMeasurementLog);
            groupBoxMeasurementSequence.Controls.Add(lblSequenceStatus);
            groupBoxMeasurementSequence.Controls.Add(btnStopSequence);
            groupBoxMeasurementSequence.Controls.Add(btnRunColorSweep);
            groupBoxMeasurementSequence.Controls.Add(btnRunGrayscale);
            groupBoxMeasurementSequence.Location = new Point(400, 20);
            groupBoxMeasurementSequence.Name = "groupBoxMeasurementSequence";
            groupBoxMeasurementSequence.Size = new Size(360, 330);
            groupBoxMeasurementSequence.TabIndex = 4;
            groupBoxMeasurementSequence.TabStop = false;
            groupBoxMeasurementSequence.Text = "Measurement Sequences";

            // btnRunGrayscale
            btnRunGrayscale.Location = new Point(15, 30);
            btnRunGrayscale.Name = "btnRunGrayscale";
            btnRunGrayscale.Size = new Size(100, 25);
            btnRunGrayscale.TabIndex = 0;
            btnRunGrayscale.Text = "10pt Grayscale";
            btnRunGrayscale.UseVisualStyleBackColor = true;
            btnRunGrayscale.Click += btnRunGrayscale_Click;

            // btnRunColorSweep
            btnRunColorSweep.Location = new Point(130, 30);
            btnRunColorSweep.Name = "btnRunColorSweep";
            btnRunColorSweep.Size = new Size(120, 25);
            btnRunColorSweep.TabIndex = 1;
            btnRunColorSweep.Text = "Primaries/Secondaries";
            btnRunColorSweep.UseVisualStyleBackColor = true;
            btnRunColorSweep.Click += btnRunColorSweep_Click;

            // btnStopSequence
            btnStopSequence.Location = new Point(270, 30);
            btnStopSequence.Name = "btnStopSequence";
            btnStopSequence.Size = new Size(75, 25);
            btnStopSequence.TabIndex = 2;
            btnStopSequence.Text = "Stop";
            btnStopSequence.UseVisualStyleBackColor = true;
            btnStopSequence.Click += btnStopSequence_Click;

            // lblSequenceStatus
            lblSequenceStatus.AutoSize = true;
            lblSequenceStatus.Location = new Point(15, 70);
            lblSequenceStatus.Name = "lblSequenceStatus";
            lblSequenceStatus.Size = new Size(110, 15);
            lblSequenceStatus.TabIndex = 3;
            lblSequenceStatus.Text = "Sequence: not running";

            // lstMeasurementLog
            lstMeasurementLog.FormattingEnabled = true;
            lstMeasurementLog.ItemHeight = 15;
            lstMeasurementLog.Location = new Point(15, 95);
            lstMeasurementLog.Name = "lstMeasurementLog";
            lstMeasurementLog.Size = new Size(330, 214);
            lstMeasurementLog.TabIndex = 4;

            // chkMeterAveraging
            chkMeterAveraging.AutoSize = true;
            chkMeterAveraging.Location = new Point(15, 60);
            chkMeterAveraging.Name = "chkMeterAveraging";
            chkMeterAveraging.Size = new Size(98, 19);
            chkMeterAveraging.TabIndex = 1;
            chkMeterAveraging.Text = "Use averaging";
            chkMeterAveraging.UseVisualStyleBackColor = true;

            // chkMeterHighRes
            chkMeterHighRes.AutoSize = true;
            chkMeterHighRes.Location = new Point(15, 30);
            chkMeterHighRes.Name = "chkMeterHighRes";
            chkMeterHighRes.Size = new Size(115, 19);
            chkMeterHighRes.TabIndex = 0;
            chkMeterHighRes.Text = "High resolution";
            chkMeterHighRes.UseVisualStyleBackColor = true;

            // lblMeterDisplayType
            lblMeterDisplayType.AutoSize = true;
            lblMeterDisplayType.Location = new Point(15, 95);
            lblMeterDisplayType.Name = "lblMeterDisplayType";
            lblMeterDisplayType.Size = new Size(136, 15);
            lblMeterDisplayType.TabIndex = 2;
            lblMeterDisplayType.Text = "Display type (optional):";

            // txtMeterDisplayType
            txtMeterDisplayType.Location = new Point(170, 92);
            txtMeterDisplayType.Name = "txtMeterDisplayType";
            txtMeterDisplayType.Size = new Size(150, 23);
            txtMeterDisplayType.TabIndex = 3;

            // btnMeterCalibrate
            btnMeterCalibrate.Location = new Point(15, 135);
            btnMeterCalibrate.Name = "btnMeterCalibrate";
            btnMeterCalibrate.Size = new Size(110, 25);
            btnMeterCalibrate.TabIndex = 4;
            btnMeterCalibrate.Text = "Calibrate";
            btnMeterCalibrate.UseVisualStyleBackColor = true;
            btnMeterCalibrate.Click += btnMeterCalibrate_Click;

            // btnMeterMeasure
            btnMeterMeasure.Location = new Point(170, 135);
            btnMeterMeasure.Name = "btnMeterMeasure";
            btnMeterMeasure.Size = new Size(110, 25);
            btnMeterMeasure.TabIndex = 5;
            btnMeterMeasure.Text = "Measure";
            btnMeterMeasure.UseVisualStyleBackColor = true;
            btnMeterMeasure.Click += btnMeterMeasure_Click;

            // lblMeterStatus
            lblMeterStatus.AutoSize = true;
            lblMeterStatus.Location = new Point(20, 370);
            lblMeterStatus.Name = "lblMeterStatus";
            lblMeterStatus.Size = new Size(107, 15);
            lblMeterStatus.TabIndex = 2;
            lblMeterStatus.Text = "Meter status: Idle";

            // lblMeterMeasurement
            lblMeterMeasurement.AutoSize = true;
            lblMeterMeasurement.Location = new Point(20, 400);
            lblMeterMeasurement.Name = "lblMeterMeasurement";
            lblMeterMeasurement.Size = new Size(191, 15);
            lblMeterMeasurement.TabIndex = 3;
            lblMeterMeasurement.Text = "Last reading: waiting for measure";

            // MainForm
            AutoScaleDimensions = new SizeF(6F, 13F);
            AutoScaleMode = AutoScaleMode.Font;
            ClientSize = new Size(800, 600);
            Controls.Add(tabControl1);
            Name = "MainForm";
            Text = "PickleCal - LG TV Calibration";
            tabControl1.ResumeLayout(false);
            tabPageConnection.ResumeLayout(false);
            tabPageConnection.PerformLayout();
            groupBox1.ResumeLayout(false);
            groupBox1.PerformLayout();
            tabPageTvControl.ResumeLayout(false);
            groupBox2.ResumeLayout(false);
            groupBox2.PerformLayout();
            tabPagePictureSettings.ResumeLayout(false);
            groupBox3.ResumeLayout(false);
            groupBox3.PerformLayout();
            tabPageWhiteBalance.ResumeLayout(false);
            tabPageMeter.ResumeLayout(false);
            tabPageMeter.PerformLayout();
            groupBoxMeterSelect.ResumeLayout(false);
            groupBoxMeterControl.ResumeLayout(false);
            groupBoxMeterControl.PerformLayout();
            groupBoxMeasurementSequence.ResumeLayout(false);
            groupBoxMeasurementSequence.PerformLayout();
            ResumeLayout(false);
        }

        private void InitializeUI()
        {
            txtTvIp.Text = "192.168.1.";
            cmbPictureMode.Items.AddRange(new[] {
                "cinema", "expert1", "game", "sports", "vivid", "standard",
                "eco", "hdr cinema", "hdr game", "hdr vivid"
            });
            cmbPictureMode.SelectedIndex = 1;
            cmbColorGamut.Items.AddRange(new[] { "auto", "extended", "wide", "srgb", "native" });
            cmbColorGamut.SelectedIndex = 0;
            cmbGamma.Items.AddRange(new[] { "low", "medium", "high1", "high2", "2.2", "2.4" });
            cmbGamma.SelectedIndex = 4;
            cmbColorTemp.Items.AddRange(new[] { "warm50", "warm40", "medium", "cool10", "cool20" });
            cmbColorTemp.SelectedIndex = 2;
            lblStatus.Text = "Ready";
            lblMeterStatus.Text = "Meter status: Not connected";
            lblMeterMeasurement.Text = "Last reading: waiting for measure";
            btnMeterDisconnect.Enabled = false;
            btnMeterMeasure.Enabled = false;
            btnMeterCalibrate.Enabled = false;
            lblSequenceStatus.Text = "Sequence: not running";
            btnStopSequence.Enabled = false;
            lstMeasurementLog.Items.Clear();
            UpdateSequenceButtons();
        }

        private async void btnConnect_Click(object sender, EventArgs e)
        {
            _tvIp = txtTvIp.Text.Trim();
            if (string.IsNullOrEmpty(_tvIp))
            {
                MessageBox.Show("Please enter the LG TV IP address", "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            btnConnect.Enabled = false;
            lblStatus.Text = $"Connecting to {_tvIp}...";

            try
            {
                _tvController = new LgTvController(_tvIp, chkSecureConnection.Checked);
                _tvController.OnStatusChange += TvController_OnStatusChange;
                _tvController.OnDisconnect += TvController_OnDisconnect;

                await _tvController.ConnectAsync();

                if (_tvController.IsConnected && _tvController.IsPaired)
                {
                    lblStatus.Text = $"Connected to {_tvIp}";
                    lblStatus.ForeColor = Color.Green;
                    btnDisconnect.Enabled = true;
                    btnConnect.Enabled = false;
                }
                else
                {
                    lblStatus.Text = "Connection failed - check pairing";
                    lblStatus.ForeColor = Color.Red;
                    btnConnect.Enabled = true;
                }
            }
            catch (Exception ex)
            {
                lblStatus.Text = $"Error: {ex.Message}";
                lblStatus.ForeColor = Color.Red;
                btnConnect.Enabled = true;
            }
        }

        private async void btnDisconnect_Click(object sender, EventArgs e)
        {
            if (_tvController != null)
            {
                await _tvController.DisconnectAsync();
                lblStatus.Text = "Disconnected";
                lblStatus.ForeColor = Color.Black;
                btnConnect.Enabled = true;
                btnDisconnect.Enabled = false;
            }
        }

        private void TvController_OnStatusChange(string status)
        {
            if (InvokeRequired)
            {
                Invoke(new Action<string>(TvController_OnStatusChange), status);
                return;
            }
            lblStatus.Text = status;
        }

        private void TvController_OnDisconnect()
        {
            if (InvokeRequired)
            {
                Invoke(new Action(TvController_OnDisconnect));
                return;
            }
            lblStatus.Text = "Disconnected";
            lblStatus.ForeColor = Color.Black;
            btnConnect.Enabled = true;
            btnDisconnect.Enabled = false;
        }

        private async void btnApplyPictureMode_Click(object sender, EventArgs e)
        {
            if (_tvController == null)
            {
                MessageBox.Show("Not connected to TV", "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            string mode = cmbPictureMode.SelectedItem?.ToString() ?? "cinema";
            await _tvController.SetPictureModeAsync(mode);
            lblStatus.Text = $"Picture mode set to {mode}";
        }

        private async void btnApplyColorSettings_Click(object sender, EventArgs e)
        {
            if (_tvController == null)
            {
                MessageBox.Show("Not connected to TV", "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            string gamut = cmbColorGamut.SelectedItem?.ToString() ?? "auto";
            string gamma = cmbGamma.SelectedItem?.ToString() ?? "2.2";
            string temp = cmbColorTemp.SelectedItem?.ToString() ?? "medium";

            await _tvController.SetColorGamutAsync(gamut);
            await _tvController.SetGammaAsync(gamma);
            await _tvController.SetColorTemperatureAsync(temp);

            lblStatus.Text = $"Color settings applied: {gamut}/{gamma}/{temp}";
        }

        private async void MainForm_LoadAsync(object? sender, EventArgs e)
        {
            await RefreshMetersAsync();
        }

        private void MainForm_FormClosing(object? sender, FormClosingEventArgs e)
        {
            _meterCancellation.Cancel();
            _sequenceCancellation?.Cancel();
            _sequenceCancellation?.Dispose();
            try
            {
                _meterManager.DisposeAsync().AsTask().GetAwaiter().GetResult();
            }
            catch
            {
                // best effort cleanup
            }
        }

        private async void btnMeterRefresh_Click(object? sender, EventArgs e)
        {
            await RefreshMetersAsync();
        }

        private async void btnMeterConnect_Click(object? sender, EventArgs e)
        {
            if (cmbMeters.SelectedItem is not MeterDescriptor descriptor)
            {
                MessageBox.Show("No meter detected. Refresh and select a device.", "Meter", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            ToggleMeterControls(false);

            try
            {
                bool selected = await _meterManager.SelectMeterAsync(descriptor.Id, _meterCancellation.Token);
                if (!selected)
                {
                    lblMeterStatus.Text = "Meter status: selection failed";
                    return;
                }

                var options = new MeterConnectOptions
                {
                    PreferredMode = MeterMeasurementMode.Display,
                    UseHighResolution = chkMeterHighRes.Checked,
                    DisplayType = string.IsNullOrWhiteSpace(txtMeterDisplayType.Text) ? null : txtMeterDisplayType.Text.Trim()
                };

                await _meterManager.ConnectActiveMeterAsync(options, _meterCancellation.Token);
            }
            catch (OperationCanceledException)
            {
                lblMeterStatus.Text = "Meter status: operation cancelled";
            }
            catch (Exception ex)
            {
                lblMeterStatus.Text = $"Meter status: {ex.Message}";
            }
            finally
            {
                ToggleMeterControls(true);
            }
        }

        private async void btnMeterDisconnect_Click(object? sender, EventArgs e)
        {
            ToggleMeterControls(false);
            try
            {
                await _meterManager.DisconnectAsync(_meterCancellation.Token);
            }
            catch (Exception ex)
            {
                lblMeterStatus.Text = $"Meter status: {ex.Message}";
            }
            finally
            {
                ToggleMeterControls(true);
            }
        }

        private async void btnMeterCalibrate_Click(object? sender, EventArgs e)
        {
            ToggleMeterControls(false);
            try
            {
                var request = new MeterCalibrationRequest(MeterMeasurementMode.Display, true);
                var result = await _meterManager.CalibrateAsync(request, _meterCancellation.Token);
                if (result != null && !result.Success)
                {
                    MessageBox.Show(result.Message ?? "Calibration failed.", "Meter", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                }
            }
            catch (OperationCanceledException)
            {
                lblMeterStatus.Text = "Meter status: calibration cancelled";
            }
            catch (Exception ex)
            {
                MessageBox.Show(ex.Message, "Meter", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            finally
            {
                ToggleMeterControls(true);
            }
        }

        private async void btnMeterMeasure_Click(object? sender, EventArgs e)
        {
            ToggleMeterControls(false);
            try
            {
                var request = new MeterMeasureRequest(MeterMeasurementMode.Display, TimeSpan.Zero, 100d, chkMeterAveraging.Checked);
                var result = await _meterManager.MeasureAsync(request, _meterCancellation.Token);
                if (result == null)
                {
                    lblMeterStatus.Text = "Meter status: no active meter";
                }
                else if (!result.Success)
                {
                    MessageBox.Show(result.Message ?? "Measurement failed.", "Meter", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                }
            }
            catch (OperationCanceledException)
            {
                lblMeterStatus.Text = "Meter status: measurement cancelled";
            }
            catch (Exception ex)
            {
                MessageBox.Show(ex.Message, "Meter", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            finally
            {
                ToggleMeterControls(true);
            }
        }

        private async void btnRunGrayscale_Click(object? sender, EventArgs e)
        {
            var sequence = MeasurementSequences.Grayscale10Point(chkMeterAveraging.Checked);
            await RunSequenceAsync(sequence);
        }

        private async void btnRunColorSweep_Click(object? sender, EventArgs e)
        {
            var sequence = MeasurementSequences.PrimarySecondarySweep(chkMeterAveraging.Checked);
            await RunSequenceAsync(sequence);
        }

        private void btnStopSequence_Click(object? sender, EventArgs e)
        {
            if (!_sequenceRunning)
            {
                return;
            }

            _sequenceCancellation?.Cancel();
        }

        private async Task RunSequenceAsync(MeasurementSequence sequence)
        {
            if (_sequenceRunning)
            {
                MessageBox.Show("A sequence is already running.", "Sequences", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            if (_lastMeterState != MeterMeasurementState.Idle)
            {
                MessageBox.Show("Meter must be connected and idle before starting a sequence.", "Sequences", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            EnsureSequenceRunner();

            _sequenceCancellation = new CancellationTokenSource();
            _sequenceRunning = true;
            UpdateSequenceButtons();
            AppendSequenceLog($"Preparing {sequence.Name}...");

            try
            {
                await _sequenceRunner!.RunAsync(sequence, _sequenceCancellation.Token);
            }
            catch (OperationCanceledException)
            {
                AppendSequenceLog("Sequence cancelled.");
                lblSequenceStatus.Text = "Sequence: cancelled";
            }
            catch (Exception ex)
            {
                AppendSequenceLog($"Sequence error: {ex.Message}");
                lblSequenceStatus.Text = "Sequence: error";
            }
            finally
            {
                _sequenceRunning = false;
                _sequenceCancellation?.Dispose();
                _sequenceCancellation = null;
                UpdateSequenceButtons();
            }
        }

        private void EnsureSequenceRunner()
        {
            if (_sequenceRunner != null)
            {
                return;
            }

            _sequenceRunner = new MeasurementQueueRunner(_meterManager);
            _sequenceRunner.SequenceStarted += SequenceRunnerOnSequenceStarted;
            _sequenceRunner.StepStarted += SequenceRunnerOnStepStarted;
            _sequenceRunner.StepCompleted += SequenceRunnerOnStepCompleted;
            _sequenceRunner.SequenceCompleted += SequenceRunnerOnSequenceCompleted;
            _sequenceRunner.StepFailed += SequenceRunnerOnStepFailed;
        }

        private void SequenceRunnerOnSequenceStarted(MeasurementSequence sequence)
        {
            lstMeasurementLog.Items.Clear();
            AppendSequenceLog($"Sequence started: {sequence.Name}");
            lblSequenceStatus.Text = $"Sequence: {sequence.Name}";
        }

        private void SequenceRunnerOnStepStarted(MeasurementStep step)
        {
            lblSequenceStatus.Text = $"Running: {step.Name}";
            AppendSequenceLog($"Step started: {step.Name}");
        }

        private void SequenceRunnerOnStepCompleted(MeasurementStep step, MeterMeasurementResult? result)
        {
            if (result != null && result.Success && result.Reading != null)
            {
                var reading = result.Reading;
                var (x, y) = reading.Chromaticity;
                AppendSequenceLog($"Step completed: {step.Name} | Y {reading.Luminance:F2} cd/m², x {x:F4}, y {y:F4}");
            }
            else
            {
                string message = result?.Message ?? "No data";
                AppendSequenceLog($"Step completed with errors: {step.Name} | {message}");
            }
        }

        private void SequenceRunnerOnStepFailed(MeasurementStep step, Exception ex)
        {
            AppendSequenceLog($"Step failed: {step.Name} | {ex.Message}");
            lblSequenceStatus.Text = $"Error on {step.Name}";
        }

        private void SequenceRunnerOnSequenceCompleted(IReadOnlyList<MeasurementStepResult> results)
        {
            int successCount = results.Count(r => r.Success);
            AppendSequenceLog($"Sequence complete: {successCount}/{results.Count} successful");
            lblSequenceStatus.Text = $"Sequence complete ({successCount}/{results.Count})";
        }

        private void AppendSequenceLog(string message)
        {
            lstMeasurementLog.Items.Add($"[{DateTime.Now:HH:mm:ss}] {message}");
            lstMeasurementLog.TopIndex = lstMeasurementLog.Items.Count - 1;
        }

        private void UpdateSequenceButtons()
        {
            if (btnRunGrayscale == null || btnRunColorSweep == null || btnStopSequence == null)
            {
                return;
            }

            bool meterReady = _lastMeterState == MeterMeasurementState.Idle;
            btnRunGrayscale.Enabled = meterReady && !_sequenceRunning;
            btnRunColorSweep.Enabled = meterReady && !_sequenceRunning;
            btnStopSequence.Enabled = _sequenceRunning;
        }

        private async Task RefreshMetersAsync()
        {
            ToggleMeterControls(false);
            try
            {
                lblMeterStatus.Text = "Meter status: scanning...";
                await _meterManager.RefreshMetersAsync(_meterCancellation.Token);
                PopulateMeterList();
                lblMeterStatus.Text = _meterManager.KnownMeters.Count > 0 ? "Meter status: select a device" : "Meter status: no meters found";
            }
            catch (OperationCanceledException)
            {
                lblMeterStatus.Text = "Meter status: scan cancelled";
            }
            catch (Exception ex)
            {
                lblMeterStatus.Text = $"Meter status: {ex.Message}";
            }
            finally
            {
                ToggleMeterControls(true);
            }
        }

        private void PopulateMeterList()
        {
            cmbMeters.BeginUpdate();
            try
            {
                cmbMeters.DataSource = null;
                var meters = _meterManager.KnownMeters.ToList();
                cmbMeters.DataSource = meters;
                cmbMeters.DisplayMember = nameof(MeterDescriptor.DisplayName);
                cmbMeters.ValueMember = nameof(MeterDescriptor.Id);
                if (meters.Count > 0)
                {
                    cmbMeters.SelectedIndex = 0;
                }
            }
            finally
            {
                cmbMeters.EndUpdate();
            }
        }

        private void MeterManagerOnStateChanged(MeterStateChangedEventArgs args)
        {
            if (InvokeRequired)
            {
                BeginInvoke(new Action(() => MeterManagerOnStateChanged(args)));
                return;
            }

            string meterName = args.Descriptor?.DisplayName ?? "None";
            _lastMeterState = args.State;
            lblMeterStatus.Text = $"Meter status: {args.State} ({meterName})";
            ApplyMeterState();
        }

        private void MeterManagerOnMeasurement(MeterMeasurementResult result)
        {
            if (InvokeRequired)
            {
                BeginInvoke(new Action(() => MeterManagerOnMeasurement(result)));
                return;
            }

            if (result.Success && result.Reading != null)
            {
                var reading = result.Reading;
                var (x, y) = reading.Chromaticity;
                string cct = reading.CorrelatedColorTemperatureK.HasValue
                    ? $", CCT {reading.CorrelatedColorTemperatureK.Value:F0}K"
                    : string.Empty;
                string deltaE = reading.DeltaE2000.HasValue ? $", ΔE {reading.DeltaE2000.Value:F2}" : string.Empty;
                lblMeterMeasurement.Text = $"Last reading: Y {reading.Luminance:F2} cd/m², x {x:F4}, y {y:F4}{cct}{deltaE}";
            }
            else
            {
                lblMeterMeasurement.Text = $"Last reading: {result.Message ?? "No data"}";
            }
        }

        private void ToggleMeterControls(bool enabled)
        {
            btnMeterRefresh.Enabled = enabled;
            if (!enabled)
            {
                btnMeterConnect.Enabled = false;
                btnMeterMeasure.Enabled = false;
                btnMeterCalibrate.Enabled = false;
                btnMeterDisconnect.Enabled = false;
            }
            else
            {
                ApplyMeterState();
            }
        }

        private void ApplyMeterState()
        {
            btnMeterConnect.Enabled = _meterManager != null && _lastMeterState == MeterMeasurementState.Disconnected && _meterManager.KnownMeters.Count > 0;
            btnMeterMeasure.Enabled = _lastMeterState == MeterMeasurementState.Idle;
            btnMeterCalibrate.Enabled = _lastMeterState == MeterMeasurementState.Idle || _lastMeterState == MeterMeasurementState.Calibrating;
            btnMeterDisconnect.Enabled = _lastMeterState != MeterMeasurementState.Disconnected;
            UpdateSequenceButtons();
        }
    }
}