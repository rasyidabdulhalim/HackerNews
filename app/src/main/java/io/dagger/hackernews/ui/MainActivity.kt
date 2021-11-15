package io.dagger.hackernews.ui

import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.work.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.dagger.hackernews.R
import io.dagger.hackernews.ui.home.HomeFragment
import io.dagger.hackernews.ui.newsType.NewsTypeFragment
import io.dagger.hackernews.utils.getSharedPrefs
import io.dagger.hackernews.utils.isFirstRun
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_main_content.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext


class MainActivity : AppCompatActivity(), CoroutineScope {

    private val superVisor = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + superVisor

    private var fragTag = "Home"
    private var toolbarTitle = "Hacker News"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setNavDrawer()
        setFragment(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.apply {
            putString("FRAG_TAG", fragTag)
            putString("TOOL_TITLE", toolbarTitle)
        }
        val frag = supportFragmentManager.findFragmentByTag(fragTag) as Fragment
        supportFragmentManager.putFragment(outState, fragTag, frag)
    }

    private fun setFragment(bundle: Bundle?) {
        if (bundle != null) {
            fragTag = bundle["FRAG_TAG"] as String
            toolbarTitle = bundle["TOOL_TITLE"] as String
            setToolBar(toolbarTitle)
            val frag = supportFragmentManager.getFragment(bundle, fragTag) as Fragment
            supportFragmentManager.beginTransaction().replace(R.id.container, frag, fragTag)
                .commit()
        } else {
            supportFragmentManager.beginTransaction().replace(R.id.container, HomeFragment(), "Home")
                .commit()
        }
    }

    private fun setFragmentMenuItem(menuItem: MenuItem, fragment: Fragment, tag: String): Boolean {
        if (supportFragmentManager.findFragmentByTag(tag) == null) {
            supportFragmentManager.beginTransaction().replace(R.id.container, fragment, tag).commit()
        }
        menuItem.isChecked = true
        drawerLayout.closeDrawer(Gravity.LEFT)
        return true
    }

    private fun setNavDrawer() {
        setSupportActionBar(matToolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val drawerToggle =
            ActionBarDrawerToggle(this, drawerLayout, matToolBar, R.string.open_drawer, R.string.close_drawer)
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        nav_view.setNavigationItemSelectedListener {
            return@setNavigationItemSelectedListener when (it.itemId) {
                R.id.action_dash -> {
                    fragTag = "Home"
                    toolbarTitle = "Hacker News"
                    setToolBar(toolbarTitle)
                    setFragmentMenuItem(it, HomeFragment(), "Home")
                }
                R.id.action_top -> {
                    fragTag = "Top"
                    toolbarTitle = "Top Stories"
                    setToolBar(toolbarTitle)
                    setFragmentMenuItem(it, NewsTypeFragment.newInstance("Top"), "Top")
                }
                R.id.action_saved -> {
                    fragTag = "Saved"
                    toolbarTitle = "My Favorite Stories"
                    setToolBar(toolbarTitle)
                    setFragmentMenuItem(it, NewsTypeFragment.newInstance("Saved"), "Saved")
                }
                else -> false
            }
        }
        setToolBar(toolbarTitle)
        nav_view.setCheckedItem(R.id.action_dash)
    }

    private fun setToolBar(type: String) {
        tvToolbarTitle.text = type
        when (type) {
            "Hacker News" -> {
                tvToolbarTitle.setTextColor(resources.getColor(R.color.colorAccent))
            }
            "Top" -> {
                tvToolbarTitle.setTextColor(resources.getColor(R.color.colorOrange))
            }
            "Saved" -> {
                tvToolbarTitle.setTextColor(resources.getColor(R.color.colorJamun))
            }
        }
    }


    override fun onDestroy() {
        coroutineContext.cancelChildren()
        super.onDestroy()
    }



}
