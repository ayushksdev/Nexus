import React, { useState, useEffect } from 'react';
import { 
  Activity, CheckCircle, XCircle, AlertTriangle, RefreshCw, 
  Cpu, Clock, Terminal, Send, Layers, RotateCcw, HelpCircle, ChevronRight
} from 'lucide-react';

const API_BASE = 'http://localhost:8080/api';

function App() {
  const [summary, setSummary] = useState({
    systemStatus: 'HEALTHY',
    pendingWork: 0,
    processingWork: 0,
    successfulWork: 0,
    failedWork: 0,
    activeIncidents: 0,
    oldestPendingWorkAgeSeconds: 0,
    workers: { total: 3, healthy: 3, failed: 0 },
    currentRelease: 'v1',
    previousRelease: 'v0',
    activeReleaseStatus: 'NONE'
  });

  const [workers, setWorkers] = useState([]);
  const [jobs, setJobs] = useState([]);
  const [timeline, setTimeline] = useState([]);
  const [incidents, setIncidents] = useState([]);
  
  // UI states
  const [deployVersion, setDeployVersion] = useState('');
  const [selectedJob, setSelectedJob] = useState(null);
  const [isSubmittingJob, setIsSubmittingJob] = useState(false);
  const [customJobId, setCustomJobId] = useState('');
  const [isPolling, setIsPolling] = useState(true);

  // Poll API for state synchronization
  useEffect(() => {
    if (!isPolling) return;

    const fetchData = async () => {
      try {
        const [sumRes, workRes, jobsRes, timeRes, incRes] = await Promise.all([
          fetch(`${API_BASE}/dashboard/summary`),
          fetch(`${API_BASE}/workers`),
          fetch(`${API_BASE}/work`),
          fetch(`${API_BASE}/dashboard/timeline`),
          fetch(`${API_BASE}/dashboard/incidents`)
        ]);

        if (sumRes.ok) setSummary(await sumRes.json());
        if (workRes.ok) setWorkers(await workRes.json());
        if (jobsRes.ok) setJobs(await jobsRes.json());
        if (timeRes.ok) setTimeline(await timeRes.json());
        if (incRes.ok) setIncidents(await incRes.json());
      } catch (err) {
        console.error('Failed to fetch data from NEXUS backend', err);
      }
    };

    fetchData(); // run immediately
    const interval = setInterval(fetchData, 1500);
    return () => clearInterval(interval);
  }, [isPolling]);

  // Handle Action - Send test job
  const handleSendJob = async (isDuplicate = false) => {
    setIsSubmittingJob(true);
    let jobId = customJobId.trim();
    if (isDuplicate) {
      if (!jobId && jobs.length > 0) {
        // Find a job ID that already exists
        jobId = jobs[0].id;
      }
    } else {
      if (!jobId) {
        jobId = 'job-' + Math.floor(Date.now() / 1000);
      }
    }
    
    try {
      const response = await fetch(`${API_BASE}/work`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: jobId,
          type: isDuplicate ? 'SEND_DUPLICATE_REPORT' : 'SEND_EMAIL',
          payload: {
            recipient: 'reviewer@nexus-platform.io',
            subject: isDuplicate ? 'Duplicate side effect check' : 'Nexus Alert',
            message: 'Checking platform idempotency behavior'
          },
          maxAttempts: 5
        })
      });
      if (response.ok) {
        setCustomJobId('');
        if (!isDuplicate) {
          alert(`Job accepted: ${jobId}`);
        } else {
          alert(`Duplicate Job Submitted: ${jobId}. Watch to verify the worker does not repeat the side effect!`);
        }
      }
    } catch (err) {
      alert('Error submitting work: ' + err.message);
    } finally {
      setIsSubmittingJob(false);
    }
  };

  // Handle Action - Set worker failure mode
  const handleSetFailureMode = async (workerId, mode) => {
    try {
      const response = await fetch(`${API_BASE}/workers/${workerId}/failure-mode`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mode })
      });
      if (!response.ok) {
        const err = await response.json();
        alert('Failed to set failure mode: ' + err.message);
      }
    } catch (err) {
      alert('Error updating failure mode: ' + err.message);
    }
  };

  // Handle Action - Recover worker
  const handleRecoverWorker = async (workerId) => {
    try {
      const response = await fetch(`${API_BASE}/workers/${workerId}/recover`, {
        method: 'POST'
      });
      if (response.ok) {
        alert(`Recovery sequence triggered for ${workerId}`);
      }
    } catch (err) {
      alert('Error recovering worker: ' + err.message);
    }
  };

  // Handle Action - Deploy release
  const handleDeployRelease = async (e) => {
    e.preventDefault();
    if (!deployVersion.trim()) return;

    try {
      const response = await fetch(`${API_BASE}/releases`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ version: deployVersion.trim() })
      });
      if (response.ok) {
        alert(`Release deployment for ${deployVersion} started!`);
        setDeployVersion('');
      } else {
        const err = await response.json();
        alert('Failed to deploy release: ' + err.message);
      }
    } catch (err) {
      alert('Error deploying release: ' + err.message);
    }
  };

  // Handle Action - Rollback release
  const handleRollback = async (releaseId) => {
    if (!confirm('Are you sure you want to rollback this release? This will revert all workers to the previous stable version immediately.')) return;
    try {
      const response = await fetch(`${API_BASE}/releases/${releaseId}/rollback`, {
        method: 'POST'
      });
      if (response.ok) {
        alert('Rollback sequence executed successfully!');
      } else {
        const err = await response.json();
        alert('Rollback failed: ' + err.message);
      }
    } catch (err) {
      alert('Error performing rollback: ' + err.message);
    }
  };

  // Handle Action - Manual retry job
  const handleManualRetryJob = async (jobId) => {
    try {
      const response = await fetch(`${API_BASE}/work/${jobId}/retry`, {
        method: 'POST'
      });
      if (response.ok) {
        alert(`Manual retry scheduled for job: ${jobId}`);
      }
    } catch (err) {
      alert('Failed to trigger retry: ' + err.message);
    }
  };

  // Open job details modal
  const handleViewJobDetails = async (jobId) => {
    try {
      const response = await fetch(`${API_BASE}/work/${jobId}`);
      if (response.ok) {
        const data = await response.json();
        setSelectedJob(data);
      }
    } catch (err) {
      alert('Error loading job details: ' + err.message);
    }
  };

  const formatAge = (seconds) => {
    if (seconds <= 0) return '0s';
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return m > 0 ? `${m}m ${s}s` : `${s}s`;
  };

  return (
    <div className="min-h-screen pb-12">
      {/* Header Panel */}
      <header className="glass border-b border-slate-800 sticky top-0 z-40 px-6 py-4">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-indigo-950/80 rounded-lg border border-indigo-500/30 text-indigo-400">
              <Activity className="h-6 w-6 animate-pulse" />
            </div>
            <div>
              <h1 className="text-xl font-bold tracking-tight text-white flex items-center gap-2">
                NEXUS
                <span className="text-xs bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-2 py-0.5 rounded-full font-mono font-medium uppercase">
                  v1.0
                </span>
              </h1>
              <p className="text-xs text-slate-400">Reliability & Work Orchestration Gateway</p>
            </div>
          </div>

          <div className="flex items-center gap-4">
            {/* Global Status badge */}
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-400 uppercase tracking-wider font-mono">Platform Health</span>
              {summary.systemStatus === 'HEALTHY' && (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-ping"></span>
                  🟢 HEALTHY
                </span>
              )}
              {summary.systemStatus === 'DEGRADED' && (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-amber-500/10 text-amber-400 border border-amber-500/20">
                  <span className="h-1.5 w-1.5 rounded-full bg-amber-400 animate-pulse"></span>
                  🟡 DEGRADED
                </span>
              )}
              {summary.systemStatus === 'FAILED' && (
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-rose-500/10 text-rose-400 border border-rose-500/20 pulse-active">
                  <span className="h-1.5 w-1.5 rounded-full bg-rose-400"></span>
                  🔴 INCIDENT DETECTED
                </span>
              )}
            </div>

            <button 
              onClick={() => setIsPolling(!isPolling)}
              className={`p-2 rounded-lg border transition-all ${
                isPolling 
                  ? 'bg-slate-900 border-slate-700 text-slate-300 hover:text-white' 
                  : 'bg-indigo-900 border-indigo-700 text-indigo-200'
              }`}
              title={isPolling ? "Pause Auto-refresh" : "Resume Auto-refresh"}
            >
              <RefreshCw className={`h-4 w-4 ${isPolling ? 'animate-spin' : ''}`} style={{ animationDuration: '3s' }} />
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-6 mt-6 space-y-6">
        
        {/* Incident Alert Panel */}
        {incidents.length > 0 && (
          <div className="bg-rose-500/5 border border-rose-500/20 rounded-xl p-5 shadow-lg">
            <div className="flex items-start gap-3">
              <div className="p-2 bg-rose-500/10 rounded-lg text-rose-400 border border-rose-500/25">
                <AlertTriangle className="h-5 w-5" />
              </div>
              <div className="flex-1">
                <h3 className="text-sm font-semibold text-rose-300">Active Platform Incidents ({incidents.length})</h3>
                <div className="mt-3 space-y-2">
                  {incidents.map((incident, i) => (
                    <div key={i} className="flex flex-col sm:flex-row sm:items-center justify-between text-xs bg-slate-950/55 p-3 rounded-lg border border-slate-900 gap-2">
                      <div className="flex items-start gap-2">
                        <span className={`px-2 py-0.5 rounded font-mono font-bold text-[10px] ${
                          incident.severity === 'HIGH' ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30' :
                          incident.severity === 'MEDIUM' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30' :
                          'bg-blue-500/20 text-blue-400 border border-blue-500/30'
                        }`}>
                          {incident.severity}
                        </span>
                        <div>
                          <p className="text-slate-200 font-medium">{incident.message}</p>
                          <p className="text-slate-400 mt-0.5">{incident.reason}</p>
                        </div>
                      </div>
                      <span className="text-[10px] text-slate-500 font-mono sm:self-center">
                        Logged: {incident.timestamp ? incident.timestamp.replace('T', ' ').substring(0, 19) : 'now'}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Metrics Grid */}
        <section className="grid grid-cols-2 md:grid-cols-5 gap-4">
          <div className="glass-card flex items-center justify-between p-4">
            <div>
              <p className="text-xs font-mono text-slate-400 uppercase tracking-wider">Pending Work</p>
              <h3 className="text-2xl font-bold mt-1 text-white">{summary.pendingWork}</h3>
            </div>
            <div className="p-2 bg-indigo-950 rounded-lg text-indigo-400 border border-indigo-900">
              <Layers className="h-5 w-5" />
            </div>
          </div>

          <div className="glass-card flex items-center justify-between p-4">
            <div>
              <p className="text-xs font-mono text-slate-400 uppercase tracking-wider">Processing</p>
              <h3 className="text-2xl font-bold mt-1 text-white">{summary.processingWork}</h3>
            </div>
            <div className="p-2 bg-blue-950 rounded-lg text-blue-400 border border-blue-900">
              <RefreshCw className="h-5 w-5 animate-spin" style={{ animationDuration: '6s' }} />
            </div>
          </div>

          <div className="glass-card flex items-center justify-between p-4">
            <div>
              <p className="text-xs font-mono text-slate-400 uppercase tracking-wider">Failed Work</p>
              <h3 className={`text-2xl font-bold mt-1 ${summary.failedWork > 0 ? 'text-rose-400' : 'text-white'}`}>
                {summary.failedWork}
              </h3>
            </div>
            <div className="p-2 bg-rose-950 rounded-lg text-rose-400 border border-rose-900">
              <XCircle className="h-5 w-5" />
            </div>
          </div>

          <div className="glass-card flex items-center justify-between p-4">
            <div>
              <p className="text-xs font-mono text-slate-400 uppercase tracking-wider">Success Work</p>
              <h3 className="text-2xl font-bold mt-1 text-emerald-400">{summary.successfulWork}</h3>
            </div>
            <div className="p-2 bg-emerald-950 rounded-lg text-emerald-400 border border-emerald-900">
              <CheckCircle className="h-5 w-5" />
            </div>
          </div>

          <div className="glass-card flex items-center justify-between p-4 col-span-2 md:col-span-1">
            <div>
              <p className="text-xs font-mono text-slate-400 uppercase tracking-wider">Oldest Pending</p>
              <h3 className={`text-2xl font-bold mt-1 ${summary.oldestPendingWorkAgeSeconds > 60 ? 'text-amber-400' : 'text-white'}`}>
                {formatAge(summary.oldestPendingWorkAgeSeconds)}
              </h3>
            </div>
            <div className="p-2 bg-slate-900 rounded-lg text-slate-400 border border-slate-800">
              <Clock className="h-5 w-5" />
            </div>
          </div>
        </section>

        {/* Dashboard Panels Layout */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          
          {/* Column 1: Workers Panel */}
          <div className="lg:col-span-2 space-y-6">
            
            {/* Workers Panel */}
            <div className="glass rounded-xl border border-slate-800 p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-base font-semibold text-white flex items-center gap-2">
                  <Cpu className="h-5 w-5 text-indigo-400" />
                  Worker Orchestration Nodes ({workers.length})
                </h2>
                <span className="text-xs font-mono text-slate-400">
                  Total Restarts Allowed: {summary.workers.total > 0 ? workers[0]?.maxRestartCount : 5}
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {workers.map((worker) => (
                  <div key={worker.id} className="bg-slate-950/70 border border-slate-900 rounded-xl p-4 flex flex-col justify-between hover:border-slate-800 transition-all">
                    <div>
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-semibold text-white">{worker.name}</span>
                        <span className="text-xs font-mono text-slate-500">Port {worker.port}</span>
                      </div>
                      
                      {/* Worker status badge */}
                      <div className="mt-2.5">
                        {worker.status === 'RUNNING' && (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-xs font-medium">
                            🟢 RUNNING
                          </span>
                        )}
                        {worker.status === 'STARTING' && (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20 text-xs font-medium animate-pulse">
                            🔵 STARTING
                          </span>
                        )}
                        {worker.status === 'RESTARTING' && (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20 text-xs font-medium animate-pulse">
                            🟡 RESTARTING
                          </span>
                        )}
                        {worker.status === 'FAILED' && (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-rose-500/10 text-rose-400 border border-rose-500/20 text-xs font-medium pulse-active">
                            🔴 FAILED
                          </span>
                        )}
                        {worker.status === 'OUT_OF_SERVICE' && (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-purple-500/10 text-purple-400 border border-purple-500/20 text-xs font-medium">
                            🚨 OUT OF SERVICE
                          </span>
                        )}
                        {worker.status === 'STOPPED' && (
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-slate-500/10 text-slate-400 border border-slate-500/20 text-xs font-medium">
                            ⚪ STOPPED
                          </span>
                        )}
                      </div>

                      {/* Worker info */}
                      <div className="mt-4 space-y-1.5 text-xs text-slate-400 font-mono">
                        <div className="flex justify-between">
                          <span>Version:</span>
                          <span className="text-white font-medium">{worker.version}</span>
                        </div>
                        <div className="flex justify-between">
                          <span>Restart Budget:</span>
                          <span className={`${worker.restartCount >= worker.maxRestartCount ? 'text-rose-400 font-bold' : 'text-white'}`}>
                            {worker.restartCount} / {worker.maxRestartCount}
                          </span>
                        </div>
                      </div>

                      {worker.lastError && (
                        <div className="mt-3 p-2 bg-slate-900 border border-slate-800 rounded text-[10px] font-mono text-slate-400 break-words max-h-16 overflow-y-auto">
                          <span className="text-rose-400 font-semibold">Error:</span> {worker.lastError}
                        </div>
                      )}
                    </div>

                    <div className="mt-4 pt-3 border-t border-slate-900 flex flex-col gap-2">
                      <button
                        onClick={() => handleRecoverWorker(worker.id)}
                        disabled={worker.status === 'RUNNING'}
                        className="w-full text-center text-xs py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:bg-slate-900 disabled:text-slate-600 disabled:border-slate-800 rounded font-medium text-white border border-indigo-500/30 transition-all"
                      >
                        Manual Recover
                      </button>

                      {/* Failure Injection Trigger for this worker */}
                      <div className="grid grid-cols-2 gap-1.5 text-[10px]">
                        <button
                          onClick={() => handleSetFailureMode(worker.id, 'NORMAL')}
                          className={`py-1 rounded text-center font-medium ${worker.failureMode === 'NORMAL' ? 'bg-emerald-950 text-emerald-400 border border-emerald-800' : 'bg-slate-900 text-slate-400 border border-slate-800 hover:text-white'}`}
                        >
                          Normal
                        </button>
                        <button
                          onClick={() => handleSetFailureMode(worker.id, 'SLOW')}
                          className={`py-1 rounded text-center font-medium ${worker.failureMode === 'SLOW' ? 'bg-amber-950 text-amber-400 border border-amber-800' : 'bg-slate-900 text-slate-400 border border-slate-800 hover:text-white'}`}
                        >
                          Slow
                        </button>
                        <button
                          onClick={() => handleSetFailureMode(worker.id, 'CRASH')}
                          className={`py-1 rounded text-center font-medium ${worker.failureMode === 'CRASH' ? 'bg-rose-950 text-rose-400 border border-rose-800' : 'bg-slate-900 text-slate-400 border border-slate-800 hover:text-white'}`}
                        >
                          Crash
                        </button>
                        <button
                          onClick={() => handleSetFailureMode(worker.id, 'CRASH_ON_START')}
                          className={`py-1 rounded text-center font-medium ${worker.failureMode === 'CRASH_ON_START' ? 'bg-purple-950 text-purple-400 border border-purple-800' : 'bg-slate-900 text-slate-400 border border-slate-800 hover:text-white'}`}
                        >
                          Crash Start
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Work Panel */}
            <div className="glass rounded-xl border border-slate-800 p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-base font-semibold text-white flex items-center gap-2">
                  <Terminal className="h-5 w-5 text-indigo-400" />
                  Execution Job Queue ({jobs.length})
                </h2>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="border-b border-slate-900 text-xs font-mono text-slate-400 uppercase tracking-wider">
                      <th className="py-2.5">Job ID</th>
                      <th className="py-2.5">Type</th>
                      <th className="py-2.5">Status</th>
                      <th className="py-2.5">Attempts</th>
                      <th className="py-2.5">Node</th>
                      <th className="py-2.5">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-900 text-xs">
                    {jobs.length === 0 ? (
                      <tr>
                        <td colSpan="6" className="py-8 text-center text-slate-500 font-mono">
                          Queue empty. Send work to observe processing.
                        </td>
                      </tr>
                    ) : (
                      jobs.map((job) => (
                        <tr key={job.id} className="hover:bg-slate-900/40 transition-colors">
                          <td className="py-3 font-mono font-medium text-white">
                            <button 
                              onClick={() => handleViewJobDetails(job.id)}
                              className="hover:underline text-indigo-400 hover:text-indigo-300 text-left"
                            >
                              {job.id}
                            </button>
                          </td>
                          <td className="py-3 font-mono text-slate-300">{job.type}</td>
                          <td className="py-3">
                            {job.status === 'PENDING' && (
                              <span className="inline-flex px-1.5 py-0.5 rounded text-[10px] font-semibold bg-slate-900 text-slate-400 border border-slate-800">
                                PENDING
                              </span>
                            )}
                            {job.status === 'PROCESSING' && (
                              <span className="inline-flex px-1.5 py-0.5 rounded text-[10px] font-semibold bg-blue-500/10 text-blue-400 border border-blue-500/20 animate-pulse">
                                PROCESSING
                              </span>
                            )}
                            {job.status === 'SUCCESS' && (
                              <span className="inline-flex px-1.5 py-0.5 rounded text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                                SUCCESS
                              </span>
                            )}
                            {job.status === 'FAILED' && (
                              <span className="inline-flex px-1.5 py-0.5 rounded text-[10px] font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
                                FAILED
                              </span>
                            )}
                          </td>
                          <td className="py-3 font-mono text-slate-400">{job.attempts} / {job.maxAttempts}</td>
                          <td className="py-3 font-mono text-slate-400">{job.assignedWorkerId || '-'}</td>
                          <td className="py-3">
                            <div className="flex gap-2">
                              <button
                                onClick={() => handleViewJobDetails(job.id)}
                                className="text-slate-300 hover:text-white font-medium hover:underline"
                              >
                                History
                              </button>
                              {job.status === 'FAILED' && (
                                <button
                                  onClick={() => handleManualRetryJob(job.id)}
                                  className="text-indigo-400 hover:text-indigo-300 font-medium hover:underline flex items-center gap-0.5"
                                >
                                  <RotateCcw className="h-3 w-3" /> Retry
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

          </div>

          {/* Column 2: Releases & Failure Lab Panel */}
          <div className="space-y-6">
            
            {/* Release Control Panel */}
            <div className="glass rounded-xl border border-slate-800 p-6">
              <h2 className="text-base font-semibold text-white flex items-center gap-2 mb-4">
                <Layers className="h-5 w-5 text-indigo-400" />
                Release Orchestrator
              </h2>

              <div className="bg-slate-950/70 border border-slate-900 rounded-xl p-4 space-y-4">
                <div className="grid grid-cols-2 gap-4 text-xs font-mono border-b border-slate-900 pb-3">
                  <div>
                    <span className="text-slate-500 block">Current Version</span>
                    <span className="text-white text-sm font-semibold">{summary.currentRelease}</span>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Previous Version</span>
                    <span className="text-white text-sm font-semibold">{summary.previousRelease}</span>
                  </div>
                </div>

                <div className="flex items-center justify-between text-xs">
                  <span className="text-slate-400">Watch Status:</span>
                  {summary.activeReleaseStatus === 'WATCHING' && (
                    <span className="inline-flex px-2 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20 font-medium font-mono animate-pulse">
                      ⏳ WATCHING (30s)
                    </span>
                  )}
                  {summary.activeReleaseStatus === 'DEPLOYING' && (
                    <span className="inline-flex px-2 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20 font-medium font-mono animate-pulse">
                      🚀 DEPLOYING
                    </span>
                  )}
                  {summary.activeReleaseStatus === 'SUCCESS' && (
                    <span className="inline-flex px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-medium font-mono">
                      ✅ STABLE
                    </span>
                  )}
                  {summary.activeReleaseStatus === 'FAILED' && (
                    <span className="inline-flex px-2 py-0.5 rounded bg-rose-500/10 text-rose-400 border border-rose-500/20 font-medium font-mono pulse-active">
                      ❌ DEPLOYMENT FAILED
                    </span>
                  )}
                  {summary.activeReleaseStatus === 'ROLLED_BACK' && (
                    <span className="inline-flex px-2 py-0.5 rounded bg-purple-500/10 text-purple-400 border border-purple-500/20 font-medium font-mono">
                      ↩️ ROLLED BACK
                    </span>
                  )}
                  {summary.activeReleaseStatus === 'NONE' && (
                    <span className="text-slate-500 font-mono">STABLE</span>
                  )}
                </div>

                {/* Deploy Version form */}
                <form onSubmit={handleDeployRelease} className="space-y-2">
                  <label className="text-[10px] font-mono text-slate-500 uppercase tracking-wider block">Deploy Upgraded Release</label>
                  <div className="flex gap-2">
                    <input 
                      type="text"
                      placeholder="e.g. v2"
                      value={deployVersion}
                      onChange={(e) => setDeployVersion(e.target.value)}
                      disabled={summary.activeReleaseStatus === 'WATCHING' || summary.activeReleaseStatus === 'DEPLOYING'}
                      className="bg-slate-900 border border-slate-800 focus:border-indigo-500 focus:outline-none rounded px-3 py-1.5 text-xs text-white placeholder-slate-600 flex-1"
                    />
                    <button 
                      type="submit"
                      disabled={summary.activeReleaseStatus === 'WATCHING' || summary.activeReleaseStatus === 'DEPLOYING'}
                      className="bg-indigo-600 hover:bg-indigo-500 disabled:bg-slate-900 disabled:text-slate-600 disabled:border-slate-850 px-3 py-1.5 rounded text-xs font-semibold text-white transition-colors"
                    >
                      Deploy
                    </button>
                  </div>
                </form>

                {/* Rollback Trigger */}
                {summary.activeReleaseStatus === 'FAILED' && (
                  <div className="bg-rose-500/10 border border-rose-500/20 rounded-lg p-3 text-xs">
                    <p className="text-rose-400 font-medium mb-2">Release v{summary.currentRelease} is broken. Revert immediately.</p>
                    <button
                      onClick={() => handleRollback(timeline.find(e => e.eventType === 'RELEASE_FAILED')?.releaseId || 1)}
                      className="w-full py-2 bg-rose-600 hover:bg-rose-500 rounded text-xs text-white font-semibold transition-colors flex items-center justify-center gap-1.5"
                    >
                      <RotateCcw className="h-3.5 w-3.5" /> Rollback to v{summary.previousRelease}
                    </button>
                  </div>
                )}

                {/* Manual Rollback Trigger (if release succeeded or rolled back but still available) */}
                {summary.activeReleaseStatus !== 'FAILED' && (
                  <button
                    onClick={() => {
                      const latestReleaseObj = timeline.find(e => e.eventType === 'RELEASE_SUCCESS' || e.eventType === 'RELEASE_FAILED');
                      if (latestReleaseObj?.releaseId) {
                        handleRollback(latestReleaseObj.releaseId);
                      } else {
                        alert('No recent release ID found in timelines to rollback.');
                      }
                    }}
                    className="w-full text-center text-xs py-2 bg-slate-900 hover:bg-slate-850 border border-slate-850 rounded text-slate-300 font-medium hover:text-white transition-all flex items-center justify-center gap-1"
                  >
                    <RotateCcw className="h-3 w-3" /> Rollback Active Release
                  </button>
                )}
              </div>
            </div>

            {/* Failure Lab Panel */}
            <div className="glass rounded-xl border border-slate-800 p-6">
              <h2 className="text-base font-semibold text-white flex items-center gap-2 mb-4">
                <HelpCircle className="h-5 w-5 text-indigo-400" />
                Failure Lab
              </h2>

              <div className="space-y-4">
                
                {/* Send Test Job Section */}
                <div className="bg-slate-950/70 border border-slate-900 rounded-xl p-4 space-y-3">
                  <h3 className="text-xs font-mono text-slate-400 uppercase tracking-wider">Test Job Publisher</h3>
                  <div className="space-y-2">
                    <input
                      type="text"
                      placeholder="Custom Job ID (optional)"
                      value={customJobId}
                      onChange={(e) => setCustomJobId(e.target.value)}
                      className="w-full bg-slate-900 border border-slate-800 focus:border-indigo-500 focus:outline-none rounded px-3 py-1.5 text-xs text-white placeholder-slate-600"
                    />
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        onClick={() => handleSendJob(false)}
                        disabled={isSubmittingJob}
                        className="py-2 bg-indigo-600 hover:bg-indigo-500 rounded text-xs text-white font-medium transition-colors flex items-center justify-center gap-1"
                      >
                        <Send className="h-3.5 w-3.5" /> Send Job
                      </button>
                      <button
                        onClick={() => handleSendJob(true)}
                        disabled={isSubmittingJob}
                        className="py-2 bg-slate-900 hover:bg-slate-850 border border-slate-800 rounded text-xs text-slate-200 font-medium transition-colors flex items-center justify-center gap-1"
                      >
                        Send Duplicate
                      </button>
                    </div>
                  </div>
                </div>

                {/* Demonstration Steps */}
                <div className="p-4 bg-slate-900/40 rounded-xl border border-slate-900 text-xs text-slate-400 space-y-2">
                  <span className="font-mono text-[10px] text-indigo-400 uppercase tracking-wider block">Verification Recipes</span>
                  <ul className="space-y-1.5 list-disc pl-4 text-[11px]">
                    <li><strong>Worker Crash:</strong> Set Worker-1 to <span className="text-slate-200">Crash</span> and submit a job. Watch retries.</li>
                    <li><strong>Idempotency:</strong> Submit a job, then submit the <span className="text-slate-200">Same ID</span> again. Watch worker skip execution.</li>
                    <li><strong>Budget Limit:</strong> Set Worker-2 to <span className="text-slate-200">Crash Start</span>. Observe <span className="text-rose-400 font-semibold">OUT_OF_SERVICE</span>.</li>
                    <li><strong>Bad Release:</strong> Deploy <span className="text-slate-200">v2</span>. Set Worker-3 to <span className="text-slate-200">Crash</span>. Observe watch failure and click <span className="text-rose-400 font-semibold">Rollback</span>.</li>
                  </ul>
                </div>

              </div>
            </div>

          </div>

        </div>

        {/* Global Event Timeline */}
        <section className="glass rounded-xl border border-slate-800 p-6">
          <h2 className="text-base font-semibold text-white flex items-center gap-2 mb-4">
            <Terminal className="h-5 w-5 text-indigo-400" />
            Global Platform Audit Log & Timeline
          </h2>

          <div className="bg-slate-950/70 border border-slate-900 rounded-xl p-4 max-h-96 overflow-y-auto space-y-3 font-mono text-xs">
            {timeline.length === 0 ? (
              <div className="text-center py-8 text-slate-600">No events captured. Submit work to generate event triggers.</div>
            ) : (
              timeline.map((event) => (
                <div 
                  key={event.id}
                  className={`flex flex-col sm:flex-row items-start sm:items-center justify-between p-3 rounded-lg border gap-3 ${
                    event.eventType.includes('CRASH') || event.eventType.includes('FAILED') ? 'bg-rose-500/5 border-rose-500/10 text-rose-200' :
                    event.eventType.includes('ROLLBACK') ? 'bg-purple-500/5 border-purple-500/10 text-purple-200' :
                    event.eventType.includes('RELEASE') ? 'bg-indigo-500/5 border-indigo-500/10 text-indigo-200' :
                    event.eventType.contains?.('RECOVER') || event.eventType.includes('SUCCESS') ? 'bg-emerald-500/5 border-emerald-500/10 text-emerald-200' :
                    'bg-slate-900/60 border-slate-850 text-slate-300'
                  }`}
                >
                  <div className="flex flex-col sm:flex-row items-start gap-2.5">
                    <span className="text-[10px] text-slate-500 self-start sm:self-center font-bold">
                      {event.timestamp ? event.timestamp.replace('T', ' ').substring(11, 19) : ''}
                    </span>
                    <span className="px-2 py-0.5 rounded text-[10px] font-bold tracking-tight bg-slate-950/90 border border-slate-800">
                      {event.eventType}
                    </span>
                    <div>
                      <p className="font-semibold text-slate-100">{event.message}</p>
                      {event.reason && <p className="text-[11px] text-slate-400 mt-0.5">Reason: {event.reason}</p>}
                    </div>
                  </div>
                  <div className="text-[10px] text-slate-500 flex items-center gap-1.5 self-end sm:self-center">
                    <span>Subj: {event.subjectType}/{event.subjectId}</span>
                    {event.releaseId && <span className="bg-indigo-950/80 border border-indigo-800 px-1 rounded text-indigo-400">Release #{event.releaseId}</span>}
                  </div>
                </div>
              ))
            )}
          </div>
        </section>

      </main>

      {/* Attempt History Modal */}
      {selectedJob && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="glass max-w-2xl w-full rounded-xl border border-slate-850 overflow-hidden shadow-2xl">
            <div className="px-6 py-4 border-b border-slate-850 flex justify-between items-center bg-slate-950/40">
              <h3 className="text-base font-semibold text-white">Job Details & Execution Attempts</h3>
              <button 
                onClick={() => setSelectedJob(null)}
                className="text-slate-400 hover:text-white font-mono text-sm p-1.5 hover:bg-slate-900 rounded"
              >
                ✕ Close
              </button>
            </div>
            
            <div className="p-6 space-y-6 max-h-[70vh] overflow-y-auto">
              
              {/* Job Info */}
              <div className="grid grid-cols-2 gap-4 text-xs font-mono bg-slate-950/70 border border-slate-900 rounded-lg p-4">
                <div>
                  <span className="text-slate-500 block">ID:</span>
                  <span className="text-white font-semibold text-sm">{selectedJob.work.id}</span>
                </div>
                <div>
                  <span className="text-slate-500 block">Type:</span>
                  <span className="text-white font-semibold">{selectedJob.work.type}</span>
                </div>
                <div>
                  <span className="text-slate-500 block">Status:</span>
                  <span className="text-white">{selectedJob.work.status}</span>
                </div>
                <div>
                  <span className="text-slate-500 block">Attempts:</span>
                  <span className="text-white">{selectedJob.work.attempts} / {selectedJob.work.maxAttempts}</span>
                </div>
                <div className="col-span-2">
                  <span className="text-slate-500 block">Payload:</span>
                  <pre className="text-[10px] bg-slate-900 p-2 rounded border border-slate-850 overflow-x-auto text-slate-300 whitespace-pre-wrap break-all">
                    {selectedJob.work.payload}
                  </pre>
                </div>
                {selectedJob.work.lastError && (
                  <div className="col-span-2 text-rose-400 bg-rose-500/5 border border-rose-500/10 p-3 rounded-lg">
                    <span className="font-bold text-[10px] block uppercase tracking-wider">Last Error Reason:</span>
                    <p className="mt-1 break-words whitespace-pre-wrap">{selectedJob.work.lastError}</p>
                  </div>
                )}
              </div>

              {/* Attempts List */}
              <div className="space-y-3">
                <h4 className="text-xs font-mono text-slate-400 uppercase tracking-wider">Execution Attempts History ({selectedJob.attempts.length})</h4>
                
                <div className="space-y-2">
                  {selectedJob.attempts.length === 0 ? (
                    <p className="text-xs font-mono text-slate-500 text-center py-4">No attempts recorded yet.</p>
                  ) : (
                    selectedJob.attempts.map((attempt, index) => (
                      <div key={attempt.id} className="bg-slate-950/65 border border-slate-900 rounded-lg p-3 text-xs space-y-1.5">
                        <div className="flex items-center justify-between">
                          <span className="font-semibold text-white font-mono">Attempt #{attempt.attemptNumber}</span>
                          <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                            attempt.status === 'SUCCESS' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' :
                            attempt.status === 'STARTED' ? 'bg-blue-500/10 text-blue-400 border border-blue-500/20' :
                            'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                          }`}>
                            {attempt.status}
                          </span>
                        </div>
                        <div className="grid grid-cols-2 gap-y-1 gap-x-4 text-[11px] font-mono text-slate-400">
                          <div>
                            <span>Worker Node:</span> <span className="text-slate-200">{attempt.workerId}</span>
                          </div>
                          <div>
                            <span>Duration:</span> <span className="text-slate-200">{attempt.durationMs ? `${attempt.durationMs}ms` : '-'}</span>
                          </div>
                          <div>
                            <span>Started:</span> <span className="text-slate-200">{attempt.startedAt.replace('T', ' ').substring(11, 19)}</span>
                          </div>
                          <div>
                            <span>Finished:</span> <span className="text-slate-200">{attempt.completedAt ? attempt.completedAt.replace('T', ' ').substring(11, 19) : '-'}</span>
                          </div>
                        </div>
                        {attempt.error && (
                          <div className="mt-2 p-2 bg-slate-900 border border-slate-850 rounded text-[10px] font-mono text-rose-300">
                            <strong>Error:</strong> {attempt.error}
                          </div>
                        )}
                      </div>
                    ))
                  )}
                </div>
              </div>

            </div>
          </div>
        </div>
      )}

    </div>
  );
}

export default App;
