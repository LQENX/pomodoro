package com.geras.pomodorotodolist.fragments

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geras.pomodorotodolist.*
import com.geras.pomodorotodolist.receivers.TimerExpiredReceiver
import com.geras.pomodorotodolist.databinding.FragmentPomodoroBinding
import com.geras.pomodorotodolist.utils.NotificationUtil
import com.geras.pomodorotodolist.utils.PrefUtil
import java.util.*


class PomodoroFragment : Fragment() {
    private var _binding: FragmentPomodoroBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get () = _binding!!

    private lateinit var timer: CountDownTimer
    private var timerLengthSeconds = 0L
    private var timerState = TimerState.Stopped

    private var secondsRemaining = 0L


    //TODO: test implementation!
    // use a more correct implementation for it later
    private val HIDE: Byte = 0
    private val SHOW: Byte = 1


    private enum class NoteButtons(val value: String) {
        BUTTON_1("ToDo"), BUTTON_2("Done"),
    }

    enum class TimerState {
        Stopped, Running
    }



    companion object {
        fun setAlarm(context: Context, nowSeconds: Long, secondsRemaining: Long): Long {
            val wakeUpTime = (nowSeconds + secondsRemaining) * 1000
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TimerExpiredReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, wakeUpTime, pendingIntent)
            PrefUtil.setAlarmSetTime(nowSeconds, context)

            return wakeUpTime
        }

        fun removeAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TimerExpiredReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0)
            alarmManager.cancel(pendingIntent)
            PrefUtil.setAlarmSetTime(0, context)
        }
        val nowSeconds: Long
            get() = Calendar.getInstance().timeInMillis / 1000

    }


    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentPomodoroBinding.inflate(inflater, container, false)
        val rootView = binding.root


        val recyclerView = binding.currentTask
        val linearLayoutManager = LinearLayoutManager(
            binding.root.context,
            RecyclerView.VERTICAL,
            false
        )
        recyclerView.layoutManager = linearLayoutManager
        val adapter = RecyclerViewAdapter(this)
        recyclerView.adapter = adapter

        initMainViewModel(adapter)
        initNoteSwipeMenu(recyclerView, adapter)

        setButtonsListeners()

        return rootView
    }


    private fun initMainViewModel(adapter: RecyclerViewAdapter) {
        val mainViewModel = ViewModelProviders.of(this@PomodoroFragment)
                .get(MainViewModel::class.java)

        mainViewModel
            .getNotesByState(Note.State.WORK_ON_IT)
            .observe(viewLifecycleOwner, object : Observer<List<Note>> {
                override fun onChanged(notes: List<Note>) {
                    adapter.setItems(notes)
                }
            } )

    }


    private fun initNoteSwipeMenu(recyclerView: RecyclerView, adapter: RecyclerViewAdapter) {
        val swipeController = object : SwipeController(this.requireContext()) {
            override fun instantiateUnderlayButton(viewHolder: RecyclerView.ViewHolder?,
                                                   underlayButtons: MutableList<UnderlayButton?>?) {

                if (underlayButtons != null) {
                    underlayButtons.add(
                        UnderlayButton(
                            NoteButtons.BUTTON_2.value, 0, Color.WHITE,
                            object : UnderlayButtonClickListener {
                                override fun onClick(position: Int) {
                                    //TODO: uncomment note move logic
                                    Toast.makeText(context, "Done coming soon",
                                        Toast.LENGTH_LONG).show()
                                    /*changeNoteState(
                                        note = adapter.getSortedList()[position],
                                        state = Note.State.DONE,
                                        movedTo = NoteButtons.BUTTON_2.value
                                    )*/
                                }

                            })
                    )

                    underlayButtons.add(
                        UnderlayButton(
                            NoteButtons.BUTTON_1.value, 0, Color.WHITE,
                            object : UnderlayButtonClickListener {
                                override fun onClick(position: Int) {
                                    changeNoteState(
                                        note = adapter.getSortedList()[position],
                                        state = Note.State.TO_DO,
                                        movedTo = NoteButtons.BUTTON_1.value
                                    )

                                }

                            })
                    )

                }
            }
        }
        swipeController.attachToRecyclerView(recyclerView)
    }

    private fun changeNoteState(note: Note, state: Note.State, movedTo: String) {
        note.state = state
        App.instance.getNoteDao().update(note)

        Toast.makeText(this.requireContext(), "Note moved to \"$movedTo\" ", Toast.LENGTH_LONG).show()
    }

    private fun setButtonsListeners() {
        binding.buttonStart.setOnClickListener {
            startTimer()
            timerState = TimerState.Running
            updateButtons()
            /*updateTaskView(SHOW)*/
        }
        binding.buttonStop.setOnClickListener {
            timer.cancel()
            onTimerFinished()
        }


        //TODO: implement view`s behaviour(buttons and etc.
        // views must will be able appear and disappear)
        /*binding.buttonStop.isVisible = false
        binding.buttonStart.isVisible = false*/

        binding.textViewCountdown.setOnClickListener {
            /*binding.buttonStop.isVisible = true
            binding.buttonStart.isVisible = true*/
            updateTaskView(HIDE)
            binding.textViewCountdown
                    .animate()
                    .translationY(300f)
                    .setDuration(500)
                    .scaleX(1.3f)
                    .scaleY(1.3f)
            //TODO: isEnabled = off

        }


        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                setNewTimerLength()
                PrefUtil.setSecondsRemaining(timerLengthSeconds, binding.root.context)
                secondsRemaining = timerLengthSeconds
                updateCountDownUI()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onResume() {
        super.onResume()

        initTimer()
        removeAlarm(binding.root.context)
        NotificationUtil.hideTimerNotification(binding.root.context)
    }

    override fun onPause() {
        super.onPause()

        if (timerState == TimerState.Running) {
            timer.cancel()
            val wakeUpTime = setAlarm(binding.root.context, nowSeconds, secondsRemaining)
            NotificationUtil.showTimerRunning(binding.root.context, wakeUpTime)
        }

        PrefUtil.setPreviousTimerLengthSeconds(timerLengthSeconds, binding.root.context)
        PrefUtil.setSecondsRemaining(secondsRemaining, binding.root.context)
        PrefUtil.setTimerState(timerState, binding.root.context)
    }

    private fun initTimer() {
        timerState = PrefUtil.getTimerState(binding.root.context)

        if (timerState == TimerState.Stopped)
            setNewTimerLength()
        else
            setPreviousTimerLength()

        secondsRemaining = if (timerState == TimerState.Running)
            PrefUtil.getSecondsRemaining(binding.root.context)
        else
            timerLengthSeconds

        val alarmSetTime = PrefUtil.getAlarmSetTime(binding.root.context)
        if (alarmSetTime > 0)
            secondsRemaining -= nowSeconds - alarmSetTime

        if (secondsRemaining <= 0)
            onTimerFinished()
        else if (timerState == TimerState.Running)
            startTimer()

        updateButtons()
        updateCountDownUI()
    }

    private fun onTimerFinished() {
        timerState = TimerState.Stopped

        setNewTimerLength()

        PrefUtil.setSecondsRemaining(timerLengthSeconds, binding.root.context)
        secondsRemaining = timerLengthSeconds

        updateButtons()
        updateCountDownUI()
    }

    private fun startTimer() {
        timerState = TimerState.Running

        timer = object : CountDownTimer(secondsRemaining * 1000, 1000) {
            override fun onFinish() {
                onTimerFinished()
            }

            override fun onTick(millisUntilFinished: Long) {
                secondsRemaining = millisUntilFinished / 1000
                updateCountDownUI()
            }
        }.start()

    }

    //TODO: implement set timer in minutes
    private fun setNewTimerLength() {
        val lengthInMinutes = PrefUtil.getTimerLength(binding.root.context)
        timerLengthSeconds = binding.seekBar.progress.toLong() /*(lengthInMinutes * 60L)*/

    }

    private fun setPreviousTimerLength() {
        timerLengthSeconds = PrefUtil.getPreviousTimerLengthSeconds(binding.root.context)
    }

    private fun updateCountDownUI() {
        val minutesUntilFinished = secondsRemaining / 60
        val secondsInMinutesUntilFinished = secondsRemaining - minutesUntilFinished * 60
        val secondsStr = secondsInMinutesUntilFinished.toString()
        binding.textViewCountdown.text = "$minutesUntilFinished:${
            if (secondsStr.length == 2) secondsStr
            else "0" + secondsStr}"
    }

    private fun updateButtons() {
        when (timerState) {
            TimerState.Running -> {
                binding.buttonStart.isEnabled = false
                binding.buttonStop.isEnabled = true
            }
            TimerState.Stopped -> {
                binding.buttonStart.isEnabled = true
                binding.buttonStop.isEnabled = false
            }
        }
    }

    private fun updateTaskView(status: Byte) {
        when (status) {
            SHOW -> {
                binding.currentTask
                        .animate()
                        .alpha(1f)
                        .setDuration(250)
            }

            HIDE -> {
                binding.currentTask
                        .animate()
                        .alpha(0f)
                        .setDuration(250)
            }
        }
    }




    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}