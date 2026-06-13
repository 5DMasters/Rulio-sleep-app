package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RulioTheme
import com.example.ui.theme.SurfaceDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RulioTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BackgroundDark
                ) { innerPadding ->
                    RulioApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

enum class BrainwavePhase(val label: String, val frequency: Float, val color: Color) {
    ALPHA("Relaxed", 10f, ElectricBlue),
    THETA("REM Sleep", 6f, ElectricPurple),
    DELTA("Deep Sleep", 2.5f, NeonCyan),
    EPSILON("Sub-Deep", 0.5f, Color(0xFF818CF8))
}

class BinauralBeatEngine {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val sampleRate = 44100
    private var carrierFreq = 200.0
    private var beatFreq = 10.0

    fun start(beat: Float) {
        if (isPlaying) stop()
        beatFreq = beat.toDouble()
        isPlaying = true
        
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        audioTrack?.play()
        
        Thread {
            val samples = ShortArray(minBufferSize)
            var angleLeft = 0.0
            var angleRight = 0.0
            val freqLeft = carrierFreq - (beatFreq / 2.0)
            val freqRight = carrierFreq + (beatFreq / 2.0)
            
            while (isPlaying) {
                for (i in 0 until minBufferSize step 2) {
                    samples[i] = (kotlin.math.sin(angleLeft) * Short.MAX_VALUE * 0.4).toInt().toShort()
                    samples[i + 1] = (kotlin.math.sin(angleRight) * Short.MAX_VALUE * 0.4).toInt().toShort()
                    
                    angleLeft += 2.0 * Math.PI * freqLeft / sampleRate
                    angleRight += 2.0 * Math.PI * freqRight / sampleRate
                    
                    if (angleLeft > 2.0 * Math.PI) angleLeft -= 2.0 * Math.PI
                    if (angleRight > 2.0 * Math.PI) angleRight -= 2.0 * Math.PI
                }
                audioTrack?.write(samples, 0, minBufferSize)
            }
        }.start()
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Already released or stopped
        }
        audioTrack = null
    }
}

class SleepViewModel : ViewModel() {
    private val engine = BinauralBeatEngine()

    private val _timerSeconds = MutableStateFlow(420) // 7 minutes default
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _currentPhase = MutableStateFlow(BrainwavePhase.ALPHA)
    val currentPhase: StateFlow<BrainwavePhase> = _currentPhase.asStateFlow()

    private val _spectrum = MutableStateFlow(FloatArray(32) { 0f })
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()

    fun toggleActive() {
        val newState = !_isActive.value
        _isActive.value = newState
        if (newState) {
            engine.start(_currentPhase.value.frequency)
        } else {
            engine.stop()
        }
    }

    fun setPhase(phase: BrainwavePhase) {
        _currentPhase.value = phase
        if (_isActive.value) {
            engine.start(phase.frequency)
        }
    }

    suspend fun runTimer() {
        while (true) {
            if (_isActive.value && _timerSeconds.value > 0) {
                delay(1000)
                _timerSeconds.value -= 1
            } else {
                delay(100)
            }
        }
    }

    suspend fun updateSpectrum() {
        while (true) {
            if (_isActive.value) {
                val newData = FloatArray(32)
                val targetIdx = 10 // Simulated peak area
                val phaseFreq = _currentPhase.value.frequency
                
                for (i in newData.indices) {
                    val dist = Math.abs(i - targetIdx).toFloat()
                    val peak = 0.8f / (1f + dist * dist)
                    newData[i] = (peak + Random.nextFloat() * 0.15f).coerceIn(0f, 1f)
                }
                _spectrum.value = newData
            } else {
                _spectrum.value = FloatArray(32) { Random.nextFloat() * 0.05f }
            }
            delay(50) // 20fps for visualizer
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.stop()
    }
}

@Composable
fun RulioApp(modifier: Modifier = Modifier, viewModel: SleepViewModel = viewModel()) {
    val timerSeconds by viewModel.timerSeconds.collectAsState()
    val isActive by viewModel.isActive.collectAsState()
    val phase by viewModel.currentPhase.collectAsState()
    val spectrum by viewModel.spectrum.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.runTimer()
    }
    
    LaunchedEffect(Unit) {
        viewModel.updateSpectrum()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RulioHeader()
        
        Spacer(modifier = Modifier.height(24.dp))

        NeuralVisualizer(phase = phase, isActive = isActive)

        Spacer(modifier = Modifier.height(24.dp))
        
        FrequencyAnalyzer(spectrum = spectrum, phaseColor = phase.color)

        Spacer(modifier = Modifier.height(24.dp))

        TimerDisplay(seconds = timerSeconds)

        Spacer(modifier = Modifier.height(24.dp))

        PhaseSelector(currentPhase = phase, onPhaseSelect = { viewModel.setPhase(it) })

        Spacer(modifier = Modifier.weight(1f))

        ControlButtons(
            isActive = isActive,
            onToggle = { viewModel.toggleActive() }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "ADAPTIVE · NEURAL · SLEEP",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 4.sp
        )
    }
}

@Composable
fun RulioHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = "Rulio Logo",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "RULIO",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun FrequencyAnalyzer(spectrum: FloatArray, phaseColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .border(1.dp, phaseColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barWidth = size.width / (spectrum.size * 1.5f)
            val spacing = barWidth * 0.5f
            
            spectrum.forEachIndexed { index, magnitude ->
                val barHeight = size.height * magnitude
                val x = index * (barWidth + spacing)
                
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(phaseColor, phaseColor.copy(alpha = 0.3f))
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
        }
    }
}

@Composable
fun NeuralVisualizer(phase: BrainwavePhase, isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (1000 / (phase.frequency / 2)).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radius"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .size(200.dp)
            .drawBehind {
                val brush = Brush.radialGradient(
                    colors = listOf(
                        phase.color.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension / 2 * pulseSize
                )
                
                drawCircle(brush = brush, radius = size.minDimension / 2 * pulseSize)
                
                if (isActive) {
                    drawCircle(
                        color = phase.color,
                        radius = size.minDimension / 3,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(SurfaceDark)
                .border(2.dp, phase.color.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = phase.color,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun TimerDisplay(seconds: Int) {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    Text(
        text = String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds),
        style = MaterialTheme.typography.displayLarge.copy(
            fontWeight = FontWeight.Light,
            letterSpacing = 4.sp
        ),
        color = Color.White
    )
}

@Composable
fun PhaseSelector(currentPhase: BrainwavePhase, onPhaseSelect: (BrainwavePhase) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(BrainwavePhase.entries) { phase ->
            val isSelected = phase == currentPhase
            Column(
                modifier = Modifier
                    .width(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) phase.color.copy(alpha = 0.2f) else SurfaceDark)
                    .border(
                        1.dp,
                        if (isSelected) phase.color else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onPhaseSelect(phase) }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = phase.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) phase.color else Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = "${phase.frequency}Hz",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = phase.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun ControlButtons(isActive: Boolean, onToggle: () -> Unit) {
    val buttonBrush = Brush.horizontalGradient(
        colors = listOf(NeonCyan, ElectricPurple)
    )

    Button(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .then(if (isActive) Modifier else Modifier.background(buttonBrush)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = if (isActive) NeonCyan else Color.White
        ),
        border = if (isActive) BorderStroke(2.dp, NeonCyan) else null
    ) {
        Text(
            text = if (isActive) "STOP ENGINE" else "START ENGINE",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}
