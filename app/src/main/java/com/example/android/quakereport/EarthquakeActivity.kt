/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.quakereport

import android.arch.lifecycle.LifecycleRegistry
import android.arch.lifecycle.LifecycleRegistryOwner
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.SharedPreferences
import android.databinding.DataBindingUtil
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import com.example.android.quakereport.databinding.ActivityEarthquakeBinding
import java.net.URL

class EarthquakeActivity : AppCompatActivity(),
        LifecycleRegistryOwner,
        SharedPreferences.OnSharedPreferenceChangeListener {

    // we do not directly extend LifecycleActivity, because it extends
    // FragmentActivity, not AppCompatActivity
    val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle() = lifecycleRegistry

    lateinit var binding: ActivityEarthquakeBinding
    lateinit var viewModel: EarthquakeViewModel
    lateinit var prefs: SharedPreferences
    val adapter = EarthquakeAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_earthquake)

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.list.setHasFixedSize(true)

        // prefs need to be initialized before calling buildURL()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(this)

        viewModel = ViewModelProviders.of(this).get(EarthquakeViewModel::class.java)
        if (viewModel.url == null) {
            viewModel.url = buildURL()
        }

        viewModel.earthquakes.observe(this, Observer {
            if (it == null) {
                binding.message = ""
                binding.swipeRefresh.isRefreshing = true
                adapter.data = emptyList()
                return@Observer
            }

            binding.swipeRefresh.isRefreshing = it.reloading
            when (it) {
                is LoadStatus.Failed -> {
                    // TODO: what if it failed for another reason?
                    binding.message = getString(R.string.no_internet_connection)
                    binding.swipeRefresh.isRefreshing = false
                    adapter.data = emptyList()
                }
                is LoadStatus.Fine -> {
                    adapter.data = it.res
                    if (it.res.isEmpty()) {
                        binding.message = getString(R.string.no_earthquakes)
                    } else {
                        binding.message = null
                    }
                }
            }
        })

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.forceReload()
        }
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        viewModel.url = buildURL()
    }

    private fun buildURL(): URL {
        val minMagnitude = prefs.getString(
                getString(R.string.settings_min_magnitude_key),
                getString(R.string.settings_min_magnitude_default)
        )
        val orderBy = prefs.getString(
                getString(R.string.settings_order_by_key),
                getString(R.string.settings_order_by_default)
        )
        val entryCount = prefs.getString(
                getString(R.string.settings_entry_count_key),
                getString(R.string.settings_entry_count_default)
        )
        val uri = Uri.parse(USGS_REQUEST_URL).buildUpon()
                .appendQueryParameter("format", "geojson")
                .appendQueryParameter("limit", entryCount)
                .appendQueryParameter("minmag", minMagnitude)
                .appendQueryParameter("orderby", orderBy)
                .build().toString()
        return URL(uri)
    }

    companion object {
        val USGS_REQUEST_URL = "https://earthquake.usgs.gov/fdsnws/event/1/query"
    }
}
