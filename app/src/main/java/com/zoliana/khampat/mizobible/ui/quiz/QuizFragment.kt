package com.zoliana.khampat.mizobible.ui.quiz

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.*
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.FragmentQuizBinding
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory
import com.zoliana.khampat.mizobible.utils.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class QuizFragment : DialogFragment() {

    private var _binding: FragmentQuizBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private var questions = mutableListOf<QuizQuestion>()
    private var currentSet = mutableListOf<QuizQuestion>()
    private var currentIndex = 0
    private var score = 0

    private var countDownTimer: CountDownTimer? = null
    private val TIMER_DURATION = 60000L // 1 minute

    private var isMinimized = false
    private var isQuizStarted = false

    private val CSV_URL = "https://docs.google.com/spreadsheets/d/1YQbAhhB45QQ-KrnubjyBtTbuwEqQLj_F_pb8AZMsk64/export?format=csv"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentQuizBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            if (isMinimized) {
                addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            } else {
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        updateVisibilityForOrientation(resources.configuration.orientation)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        applyTheme()

        binding.btnClose.setOnClickListener { exitQuiz() }
        binding.btnMinimize.setOnClickListener { toggleMinimize() }

        binding.btnStartQuizMain.setOnClickListener {
            isQuizStarted = true
            binding.instructionsCard.visibility = View.GONE
            binding.quizDimBackground.visibility = View.VISIBLE
            binding.quizDimBackground.setBackgroundColor(Color.parseColor("#99000000"))
            dialog?.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.6f)
            }

            updateVisibilityForOrientation(resources.configuration.orientation)
            loadQuizData()
        }

        binding.btnOptionA.setOnClickListener { checkAnswer("A") }
        binding.btnOptionB.setOnClickListener { checkAnswer("B") }
        binding.btnOptionC.setOnClickListener { checkAnswer("C") }
        binding.btnOptionD.setOnClickListener { checkAnswer("D") }

        binding.btnNext.setOnClickListener {
            currentIndex++
            showQuestion()
        }

        var initialTouchX = 0f
        var initialTouchY = 0f
        var initialWinX = 0
        var initialWinY = 0

        binding.quizHeaderBar.setOnTouchListener { _, event ->
            val window = dialog?.window ?: return@setOnTouchListener false
            val params = window.attributes
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    val location = IntArray(2)
                    window.decorView.getLocationOnScreen(location)
                    initialWinX = location[0]
                    initialWinY = location[1]
                    window.setGravity(Gravity.TOP or Gravity.START)
                    params.x = initialWinX; params.y = initialWinY
                    window.attributes = params
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    params.x = initialWinX + dx; params.y = initialWinY + dy
                    window.attributes = params
                    true
                }
                else -> false
            }
        }

        updateVisibilityForOrientation(resources.configuration.orientation)
        if (isQuizStarted || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            loadQuizData()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateVisibilityForOrientation(newConfig.orientation)
        if ((isQuizStarted || newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) && questions.isEmpty()) {
            loadQuizData()
        }
    }

    private fun updateVisibilityForOrientation(orientation: Int) {
        val window = dialog?.window ?: return
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        if (isQuizStarted || orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.instructionsCard.visibility = View.GONE
            binding.quizWindowCard.visibility = View.VISIBLE
            binding.quizDimBackground.visibility = View.VISIBLE
            binding.quizDimBackground.setBackgroundColor(Color.TRANSPARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.6f)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            binding.instructionsCard.visibility = View.VISIBLE
            binding.quizWindowCard.visibility = View.GONE
            binding.quizDimBackground.visibility = View.VISIBLE
            binding.quizDimBackground.setBackgroundColor(Color.parseColor("#AA000000"))
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.6f)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun playSound(fileName: String) {
        try {
            val mediaPlayer = MediaPlayer()
            val afd = requireContext().assets.openFd("sound/$fileName")
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close(); mediaPlayer.prepare(); mediaPlayer.start()
            mediaPlayer.setOnCompletionListener { it.release() }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun toggleMinimize() {
        isMinimized = !isMinimized
        val window = dialog?.window ?: return
        val cardParams = binding.quizWindowCard.layoutParams as ConstraintLayout.LayoutParams
        val titleParams = binding.textQuizTitle.layoutParams as ConstraintLayout.LayoutParams

        if (isMinimized) {
            binding.quizBodyContainer.visibility = View.GONE
            binding.quizDimBackground.visibility = View.GONE
            cardParams.width = ConstraintLayout.LayoutParams.WRAP_CONTENT
            binding.quizWindowCard.layoutParams = cardParams
            titleParams.width = ConstraintLayout.LayoutParams.WRAP_CONTENT
            binding.textQuizTitle.layoutParams = titleParams
            window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.TOP or Gravity.START)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            binding.btnMinimize.setImageResource(R.drawable.ic_check_24)
        } else {
            binding.quizBodyContainer.visibility = View.VISIBLE
            binding.quizDimBackground.visibility = View.VISIBLE
            cardParams.width = 0 
            binding.quizWindowCard.layoutParams = cardParams
            titleParams.width = 0
            binding.textQuizTitle.layoutParams = titleParams
            updateVisibilityForOrientation(resources.configuration.orientation)
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setGravity(Gravity.CENTER)
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val params = window.attributes; params.x = 0; params.y = 0; window.attributes = params
            binding.btnMinimize.setImageResource(R.drawable.ic_remove_24)
        }
    }

    private fun exitQuiz() {
        findNavController().navigateUp()
    }

    private fun loadQuizData() {
        if (_binding == null || !questions.isEmpty()) return
        binding.progressLoading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val csvData = URL(CSV_URL).readText()
                val allQuestions = parseCsv(csvData)
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    questions.clear(); questions.addAll(allQuestions)
                    binding.progressLoading.visibility = View.GONE
                    startNewSet()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.progressLoading.visibility = View.GONE
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startNewSet() {
        if (questions.isEmpty()) return
        currentSet = if (questions.size < 10) questions.shuffled().toMutableList()
        else questions.shuffled().take(10).toMutableList()
        currentIndex = 0; score = 0; showQuestion()
    }

    private fun parseCsv(data: String): List<QuizQuestion> {
        val list = mutableListOf<QuizQuestion>()
        val lines = data.split(Regex("\\r?\\n"))
        for (i in 1 until lines.size) {
            val line = lines[i].trim(); if (line.isEmpty()) continue
            val parts = parseCsvLine(line)
            if (parts.size >= 7) {
                val question = parts[1]; val optA = parts[2]; val optB = parts[3]; val optC = parts[4]; val optD = parts[5]
                val rawAnswer = parts[6].uppercase(); val finalAnswer = rawAnswer.replace("OPTION", "").trim()
                if (question.isNotEmpty()) list.add(QuizQuestion(question, optA, optB, optC, optD, finalAnswer))
            }
        }
        return list
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>(); var curVal = StringBuilder(); var inQuotes = false
        for (ch in line) {
            if (ch == '\"') inQuotes = !inQuotes
            else if (ch == ',' && !inQuotes) { result.add(curVal.toString().trim()); curVal = StringBuilder() }
            else curVal.append(ch)
        }
        result.add(curVal.toString().trim()); return result
    }

    private fun showQuestion() {
        countDownTimer?.cancel()
        binding.btnNext.visibility = View.GONE
        resetButtonStyles(); enableButtons(true)
        if (currentIndex < currentSet.size) {
            val q = currentSet[currentIndex]
            binding.textQuestion.text = q.question
            binding.btnOptionA.text = "A: ${q.optionA}"; binding.btnOptionB.text = "B: ${q.optionB}"
            binding.btnOptionC.text = "C: ${q.optionC}"; binding.btnOptionD.text = "D: ${q.optionD}"
            binding.textQuestionCount.text = "Question: ${currentIndex + 1}/${currentSet.size}"
            startTimer()
        } else handleQuizEnd()
    }

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(TIMER_DURATION, 1000) {
            override fun onTick(millisUntilFinished: Long) { binding.textTimer.text = "${millisUntilFinished / 1000}s" }
            override fun onFinish() { binding.textTimer.text = "0s"; checkAnswer("NONE") }
        }.start()
    }

    private fun applyTheme() {
        val ctx = context ?: return
        val isDark = ThemeHelper.isCurrentThemeDark(ctx)
        val density = resources.displayMetrics.density

        val cardColor = if (isDark) {
            ThemeHelper.getEffectiveCardColor(ctx) ?: Color.parseColor("#1C1E2D")
        } else {
            Color.parseColor("#FFFFFF")
        }
        val primaryColor = ThemeHelper.getPrimaryColor(ctx)
        val textColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#1A202C")
        val subTextColor = if (isDark) Color.parseColor("#CBD5E1") else Color.parseColor("#4A5568")
        val strokeColor = if (isDark) Color.parseColor("#4B5563") else Color.parseColor("#CBD5E1")

        // 1. Scrim
        binding.quizDimBackground.setBackgroundColor(Color.parseColor("#99000000"))

        // 2. Instructions Card
        binding.instructionsCard.setCardBackgroundColor(cardColor)
        binding.instructionsCard.strokeColor = strokeColor
        binding.instructionsCard.strokeWidth = (1.5f * density).toInt()
        binding.textQuizInstructionsTitle?.setTextColor(primaryColor)
        binding.textQuizInstructions.setTextColor(textColor)
        binding.btnStartQuizMain.backgroundTintList = ColorStateList.valueOf(primaryColor)
        binding.btnStartQuizMain.setTextColor(ThemeHelper.getContrastingTextColor(primaryColor))

        // 3. Quiz Window Card
        binding.quizWindowCard.setCardBackgroundColor(cardColor)
        binding.quizWindowCard.strokeColor = strokeColor
        binding.quizWindowCard.strokeWidth = (1.5f * density).toInt()

        val headerBarBg = if (isDark) Color.parseColor("#26283B") else ColorUtils.setAlphaComponent(primaryColor, 22)
        binding.quizHeaderBar.setBackgroundColor(headerBarBg)
        binding.textQuizTitle.setTextColor(primaryColor)
        binding.btnMinimize.setColorFilter(primaryColor)
        binding.btnClose.setColorFilter(Color.parseColor("#E53935"))

        binding.textTimer.setTextColor(Color.parseColor("#E53935"))
        binding.textQuestionCount.setTextColor(subTextColor)

        val questionBoxBg = if (isDark) Color.parseColor("#25283E") else Color.parseColor("#F1F5F9")
        val questionBoxStroke = if (isDark) Color.parseColor("#475569") else ColorUtils.setAlphaComponent(primaryColor, 130)
        val qDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            setColor(questionBoxBg)
            setStroke((1.5f * density).toInt(), questionBoxStroke)
        }
        binding.layoutQuestion.background = qDrawable
        binding.textQuestion.setTextColor(textColor)
        binding.textQuestion.setTypeface(null, Typeface.BOLD)

        binding.btnNext.backgroundTintList = ColorStateList.valueOf(primaryColor)
        binding.btnNext.setTextColor(ThemeHelper.getContrastingTextColor(primaryColor))

        resetButtonStyles()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    private fun checkAnswer(selected: String) {
        if (currentSet.isEmpty() || currentIndex >= currentSet.size) return
        countDownTimer?.cancel(); enableButtons(false)
        val q = currentSet[currentIndex]
        val isCorrect = selected.equals(q.answer, ignoreCase = true)
        val selectedButton = when (selected) { "A" -> binding.btnOptionA; "B" -> binding.btnOptionB; "C" -> binding.btnOptionC; "D" -> binding.btnOptionD; else -> null }
        val correctButton = when (q.answer) { "A" -> binding.btnOptionA; "B" -> binding.btnOptionB; "C" -> binding.btnOptionC; "D" -> binding.btnOptionD; else -> null }
        val density = resources.displayMetrics.density

        if (isCorrect) {
            score++; playSound("correctans.mpeg")
            selectedButton?.let { btn ->
                val correctBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12f * density
                    setColor(Color.parseColor("#2E7D32"))
                    setStroke((2f * density).toInt(), Color.parseColor("#81C784"))
                }
                btn.background = correctBg
                btn.backgroundTintList = null
                btn.setTextColor(Color.WHITE)
            }
        } else {
            vibratePhone(); playSound("wrongans.mpeg")
            selectedButton?.let { btn ->
                val wrongBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12f * density
                    setColor(Color.parseColor("#C62828"))
                    setStroke((2f * density).toInt(), Color.parseColor("#EF9A9A"))
                }
                btn.background = wrongBg
                btn.backgroundTintList = null
                btn.setTextColor(Color.WHITE)
                val animator = ObjectAnimator.ofFloat(btn, "alpha", 1f, 0.3f, 1f)
                animator.duration = 200
                animator.repeatCount = 3
                animator.start()
            }
            correctButton?.let { btn ->
                val correctBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12f * density
                    setColor(Color.parseColor("#2E7D32"))
                    setStroke((2f * density).toInt(), Color.parseColor("#81C784"))
                }
                btn.background = correctBg
                btn.backgroundTintList = null
                btn.setTextColor(Color.WHITE)
            }
        }
        binding.btnNext.visibility = View.VISIBLE
    }

    private fun handleQuizEnd() {
        playSound("complete.aac")
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("QUIZ COMPLETED")
            .setMessage("I score chu $score/${currentSet.size} a ni e.\nKan lawm e!")
            .setPositiveButton("OK") { _, _ -> exitQuiz() }
            .setCancelable(false)
            .create()
        dialog.show()
        (activity as? MainActivity)?.limitDialogWidth(dialog)
    }

    private fun vibratePhone() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(300)
    }

    private fun resetButtonStyles() {
        val ctx = context ?: return
        val isDark = ThemeHelper.isCurrentThemeDark(ctx)
        val density = resources.displayMetrics.density

        val optBgColor = if (isDark) Color.parseColor("#282B40") else Color.parseColor("#F8FAFC")
        val optStrokeColor = if (isDark) Color.parseColor("#4A4F6B") else Color.parseColor("#CBD5E1")
        val optTextColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#1A202C")

        listOf(binding.btnOptionA, binding.btnOptionB, binding.btnOptionC, binding.btnOptionD).forEach { btn ->
            val optDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(optBgColor)
                setStroke((1.5f * density).toInt(), optStrokeColor)
            }
            btn.background = optDrawable
            btn.backgroundTintList = null
            btn.setTextColor(optTextColor)
            btn.setTypeface(null, Typeface.BOLD)
            btn.alpha = 1.0f
        }
    }
    private fun enableButtons(enabled: Boolean) { binding.btnOptionA.isEnabled = enabled; binding.btnOptionB.isEnabled = enabled; binding.btnOptionC.isEnabled = enabled; binding.btnOptionD.isEnabled = enabled }

    override fun onDestroyView() { countDownTimer?.cancel(); super.onDestroyView(); _binding = null }
}
