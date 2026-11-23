// Smart Performance Analytics - teacher dashboard
// This script talks to the Java program through the /api/... addresses (see ApiHandler.java).

const $ = (id) => document.getElementById(id);

let currentPage = 'overview';
let allStudents = [];          // last list of students received from Java (used by the search box)
let attendanceLimit = 75;      // replaced by the value sent from Java
let pendingMarksRoll = null;   // set when "Enter marks" is clicked inside the student popup

// ======================= small helper functions =======================

// Makes text safe to put inside HTML
function esc(text) {
  return String(text).replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function gradeBadge(grade) {
  return `<span class="badge g-${esc(grade.replace('+', 'p'))}">${esc(grade)}</span>`;
}

// Shows a small message in the top-right corner
let toastTimer;
function toast(message, isError) {
  const box = $('toast');
  box.textContent = message;
  box.className = isError ? 'toast error' : 'toast';
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => box.classList.add('hidden'), 4000);
}

// GET request, returns the JSON sent by Java
async function getJson(url) {
  const res = await fetch(url);
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || 'Request failed');
  return data;
}

// POST request with form fields, returns the JSON sent by Java
async function postJson(url, fields) {
  const res = await fetch(url, { method: 'POST', body: new URLSearchParams(fields) });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || 'Request failed');
  return data;
}

// Runs an action and shows any error as a message instead of breaking the page
async function safely(action) {
  try {
    await action();
  } catch (e) {
    const offline = e instanceof TypeError;
    toast(offline ? 'Cannot reach the server. Is the Java program running?' : e.message, true);
  }
}

function fillSelect(select, items, valueOf, labelOf, selected) {
  select.innerHTML = items
    .map((item) => `<option value="${esc(valueOf(item))}">${esc(labelOf(item))}</option>`)
    .join('');
  if (selected && items.some((item) => valueOf(item) === selected)) {
    select.value = selected;
  }
}

// ======================= page switching =======================

const loaders = {
  overview: loadOverview,
  students: loadStudents,
  subjects: loadSubjects,
  marks: loadMarksPage,
  ranking: loadRanking,
  summary: loadSummary,
  reports: loadReports,
};

function showPage(name) {
  currentPage = name;
  document.querySelectorAll('.page').forEach((p) => p.classList.remove('active'));
  $('page-' + name).classList.add('active');
  document.querySelectorAll('#nav button').forEach((b) =>
    b.classList.toggle('active', b.dataset.page === name));
  safely(loaders[name]);
}

document.querySelectorAll('#nav button').forEach((button) =>
  button.addEventListener('click', () => showPage(button.dataset.page)));

// ======================= overview =======================

async function loadOverview() {
  const d = await getJson('/api/dashboard');
  attendanceLimit = d.attendanceLimit;
  $('storage-label').textContent = d.storage;
  $('empty-banner').classList.toggle('hidden', d.students > 0);

  $('stat-students').textContent = d.students;
  $('stat-subjects').textContent = d.subjects;
  $('stat-average').textContent = d.studentsWithMarks ? d.classAverage.toFixed(1) + '%' : '-';
  $('stat-pass').textContent = d.studentsWithMarks ? d.passPercentage.toFixed(1) + '%' : '-';

  // top performers
  $('top-list').innerHTML = d.top.length
    ? d.top.map((t) => `
        <div class="top-item">
          <span class="rank-${t.rank}">#${t.rank}</span>
          <span class="top-name">${esc(t.name)}</span>
          <span>${t.percentage.toFixed(1)}%</span>
          ${gradeBadge(t.grade)}
        </div>`).join('')
    : '<div class="empty">No marks entered yet.</div>';

  // grade distribution (bar length = number of students)
  const maxCount = Math.max(1, ...d.gradeDistribution.map((g) => g.count));
  $('grade-bars').innerHTML = d.gradeDistribution.map((g) => `
    <div class="bar-row">
      <span class="bar-label">${gradeBadge(g.grade)}</span>
      <div class="bar-track"><div class="bar-fill" style="width:${g.count / maxCount * 100}%"></div></div>
      <span class="bar-value">${g.count}</span>
    </div>`).join('');

  // subject averages (bar length = average out of 100)
  $('subject-bars').innerHTML = d.subjectAverages.length
    ? d.subjectAverages.map((s) => `
        <div class="bar-row">
          <span class="bar-label" title="${esc(s.subject)}">${esc(s.subject)}</span>
          <div class="bar-track"><div class="bar-fill" style="width:${s.average}%"></div></div>
          <span class="bar-value">${s.average.toFixed(1)}</span>
        </div>`).join('')
    : '<div class="empty">No subjects yet.</div>';

  // students who need attention
  $('attention-hint').textContent =
    `Average attendance below ${d.attendanceLimit}% or a failed subject (grade F).`;
  $('attention-list').innerHTML = d.attention.length
    ? d.attention.map((a) => `
        <div class="attention-item">${esc(a.name)} (${esc(a.rollNo)})<small>${esc(a.reason)}</small></div>`).join('')
    : '<div class="empty">Nobody needs attention right now.</div>';
}

async function loadSample() {
  const result = await postJson('/api/sample/load', {});
  toast(result.message);
  showPage(currentPage);
}

$('load-sample-overview').addEventListener('click', () => safely(loadSample));
$('load-sample').addEventListener('click', () => {
  if (confirm('This replaces ALL current students, subjects and marks with the sample data. Continue?')) {
    safely(loadSample);
  }
});

// ======================= students =======================

async function loadStudents() {
  allStudents = await getJson('/api/students');
  renderStudents();
}

function renderStudents() {
  const filter = $('student-search').value.trim().toLowerCase();
  const rows = allStudents.filter((s) =>
    s.rollNo.toLowerCase().includes(filter) || s.name.toLowerCase().includes(filter));

  if (rows.length === 0) {
    $('student-rows').innerHTML =
      `<tr><td colspan="9" class="empty">${allStudents.length ? 'No student matches your search.' : 'No students yet. Register one above.'}</td></tr>`;
    return;
  }
  $('student-rows').innerHTML = rows.map((s) => {
    const hasMarks = s.subjectCount > 0;
    return `
      <tr>
        <td>${esc(s.rollNo)}</td>
        <td>${esc(s.name)}</td>
        <td>${s.subjectCount}</td>
        <td>${hasMarks ? s.total.toFixed(2) : '-'}</td>
        <td>${hasMarks ? s.percentage.toFixed(2) + '%' : '-'}</td>
        <td class="${hasMarks && s.attendance < attendanceLimit ? 'low' : ''}">${hasMarks ? s.attendance.toFixed(1) + '%' : '-'}</td>
        <td>${gradeBadge(s.grade)}</td>
        <td>${hasMarks ? '#' + s.rank : '-'}</td>
        <td class="actions">
          <button class="link-btn" data-action="view" data-roll="${esc(s.rollNo)}">View</button>
          <button class="link-btn" data-action="rename" data-roll="${esc(s.rollNo)}">Rename</button>
          <button class="link-btn danger" data-action="delete" data-roll="${esc(s.rollNo)}">Delete</button>
        </td>
      </tr>`;
  }).join('');
}

$('student-search').addEventListener('input', renderStudents);

$('student-form').addEventListener('submit', (event) => {
  event.preventDefault();
  safely(async () => {
    const result = await postJson('/api/student/add', {
      roll: $('new-roll').value, name: $('new-name').value });
    toast(result.message);
    $('student-form').reset();
    await loadStudents();
  });
});

$('student-rows').addEventListener('click', (event) => {
  const button = event.target.closest('button');
  if (!button) return;
  const roll = button.dataset.roll;
  const student = allStudents.find((s) => s.rollNo === roll);

  safely(async () => {
    if (button.dataset.action === 'view') {
      await openStudent(roll);
    } else if (button.dataset.action === 'rename') {
      const name = prompt('New name for ' + roll + ':', student.name);
      if (name === null) return;
      toast((await postJson('/api/student/rename', { roll, name })).message);
      await loadStudents();
    } else if (button.dataset.action === 'delete') {
      if (!confirm('Delete ' + student.name + ' (' + roll + ') and all of their marks?')) return;
      toast((await postJson('/api/student/delete', { roll })).message);
      await loadStudents();
    }
  });
});

// ---------- student popup ----------

async function openStudent(roll) {
  const s = await getJson('/api/student?roll=' + encodeURIComponent(roll));
  const hasMarks = s.records.length > 0;

  const rows = s.records.map((r) => `
    <tr>
      <td>${esc(r.subject)}</td>
      <td>${r.assessment.toFixed(1)}</td>
      <td>${r.assignment.toFixed(1)}</td>
      <td>${r.attendance.toFixed(1)}% (${r.attendanceMarks.toFixed(1)} marks)</td>
      <td><strong>${r.total.toFixed(2)}</strong></td>
      <td>${gradeBadge(r.grade)}</td>
      <td><button class="link-btn danger" data-delete-subject="${esc(r.subject)}">Remove</button></td>
    </tr>`).join('');

  $('modal-content').innerHTML = `
    <h2>${esc(s.name)}</h2>
    <div class="modal-meta">Roll number ${esc(s.rollNo)}</div>
    ${hasMarks ? `
      <div class="modal-summary">
        <div>Rank<strong>#${s.rank}</strong></div>
        <div>Total<strong>${s.total.toFixed(2)}</strong></div>
        <div>Average<strong>${s.average.toFixed(2)}</strong></div>
        <div>Percentage<strong>${s.percentage.toFixed(2)}%</strong></div>
        <div>Attendance<strong>${s.attendance.toFixed(1)}%</strong></div>
        <div>Grade<strong>${gradeBadge(s.grade)}</strong></div>
      </div>
      ${s.attention ? `<p class="low">Needs attention: ${esc(s.attention)}</p>` : ''}
      <div class="table-wrap"><table>
        <thead><tr><th>Subject</th><th>Assessment /70</th><th>Assignment /20</th><th>Attendance</th><th>Total /100</th><th>Grade</th><th></th></tr></thead>
        <tbody>${rows}</tbody>
      </table></div>`
    : '<p class="empty">No marks entered for this student yet.</p>'}
    <div class="button-row" style="margin-top:16px">
      <button class="btn" id="popup-enter-marks">Enter / edit marks</button>
      ${hasMarks ? `<a class="btn btn-outline" href="/api/report/student?roll=${encodeURIComponent(s.rollNo)}&download=1">Download report</a>` : ''}
    </div>`;

  $('modal').classList.remove('hidden');

  $('popup-enter-marks').onclick = () => {
    pendingMarksRoll = s.rollNo;
    closeModal();
    showPage('marks');
  };
  $('modal-content').querySelectorAll('[data-delete-subject]').forEach((button) => {
    button.onclick = () => safely(async () => {
      const subject = button.dataset.deleteSubject;
      if (!confirm('Remove ' + s.name + "'s marks in " + subject + '?')) return;
      toast((await postJson('/api/marks/delete', { roll: s.rollNo, subject })).message);
      await openStudent(s.rollNo);
      if (currentPage === 'students') await loadStudents();
    });
  });
}

function closeModal() {
  $('modal').classList.add('hidden');
}
$('modal-close').addEventListener('click', closeModal);
$('modal').addEventListener('click', (event) => { if (event.target === $('modal')) closeModal(); });
document.addEventListener('keydown', (event) => { if (event.key === 'Escape') closeModal(); });

// ======================= subjects =======================

async function loadSubjects() {
  const subjects = await getJson('/api/subjects');
  $('subject-rows').innerHTML = subjects.length
    ? subjects.map((s) => `
        <tr>
          <td>${esc(s.name)}</td>
          <td>${s.students}</td>
          <td class="actions"><button class="link-btn danger" data-subject="${esc(s.name)}">Delete</button></td>
        </tr>`).join('')
    : '<tr><td colspan="3" class="empty">No subjects yet. Add one above.</td></tr>';
}

$('subject-form').addEventListener('submit', (event) => {
  event.preventDefault();
  safely(async () => {
    toast((await postJson('/api/subject/add', { name: $('new-subject').value })).message);
    $('subject-form').reset();
    await loadSubjects();
  });
});

$('subject-rows').addEventListener('click', (event) => {
  const button = event.target.closest('button');
  if (!button) return;
  const name = button.dataset.subject;
  if (!confirm('Delete the subject ' + name + '? All marks entered in it will be removed too.')) return;
  safely(async () => {
    toast((await postJson('/api/subject/delete', { name })).message);
    await loadSubjects();
  });
});

// ======================= enter marks =======================

async function loadMarksPage() {
  const [students, subjects] = await Promise.all([getJson('/api/students'), getJson('/api/subjects')]);

  fillSelect($('marks-student'), students, (s) => s.rollNo,
    (s) => s.rollNo + ' - ' + s.name, pendingMarksRoll || $('marks-student').value);
  pendingMarksRoll = null;
  fillSelect($('marks-subject'), subjects, (s) => s.name, (s) => s.name, $('marks-subject').value);

  $('marks-hint').textContent =
    !students.length ? 'Add a student first (Students page).'
    : !subjects.length ? 'Add a subject first (Subjects page).'
    : '';
  $('marks-result').classList.add('hidden');
  await refreshMarksForm();
}

// Shows the marks already saved for the selected student and fills the form if this subject has marks
async function refreshMarksForm() {
  const roll = $('marks-student').value;
  const subject = $('marks-subject').value;
  if (!roll) {
    $('marks-rows').innerHTML = '<tr><td colspan="6" class="empty">No student selected.</td></tr>';
    $('marks-table-title').textContent = 'Marks entered';
    return;
  }
  const s = await getJson('/api/student?roll=' + encodeURIComponent(roll));
  $('marks-table-title').textContent = 'Marks entered for ' + s.name;

  const record = s.records.find((r) => r.subject === subject);
  $('marks-assessment').value = record ? record.assessment : '';
  $('marks-assignment').value = record ? record.assignment : '';
  $('marks-attendance').value = record ? record.attendance : '';

  $('marks-rows').innerHTML = s.records.length
    ? s.records.map((r) => `
        <tr class="clickable" data-subject="${esc(r.subject)}">
          <td>${esc(r.subject)}</td>
          <td>${r.assessment.toFixed(1)}</td>
          <td>${r.assignment.toFixed(1)}</td>
          <td>${r.attendance.toFixed(1)}%</td>
          <td><strong>${r.total.toFixed(2)}</strong></td>
          <td>${gradeBadge(r.grade)}</td>
        </tr>`).join('')
    : '<tr><td colspan="6" class="empty">No marks entered for this student yet.</td></tr>';
}

$('marks-student').addEventListener('change', () => safely(refreshMarksForm));
$('marks-subject').addEventListener('change', () => safely(refreshMarksForm));

// clicking a row of the table selects that subject for editing
$('marks-rows').addEventListener('click', (event) => {
  const row = event.target.closest('tr[data-subject]');
  if (row) {
    $('marks-subject').value = row.dataset.subject;
    safely(refreshMarksForm);
  }
});

$('marks-form').addEventListener('submit', (event) => {
  event.preventDefault();
  $('marks-result').classList.add('hidden'); // do not leave an old "Saved" message on screen
  safely(async () => {
    const result = await postJson('/api/marks/save', {
      roll: $('marks-student').value,
      subject: $('marks-subject').value,
      assessment: $('marks-assessment').value,
      assignment: $('marks-assignment').value,
      attendance: $('marks-attendance').value,
    });
    $('marks-result').innerHTML =
      `${esc(result.message)} Subject total: <strong>${result.total.toFixed(2)} / 100</strong> &nbsp; Grade: ${gradeBadge(result.grade)}`;
    $('marks-result').classList.remove('hidden');
    await refreshMarksForm();
  });
});

// ======================= ranking and summary =======================

async function loadRanking() {
  const ranks = await getJson('/api/ranking');
  $('ranking-rows').innerHTML = ranks.length
    ? ranks.map((r) => `
        <tr>
          <td class="rank-${r.rank}">#${r.rank}</td>
          <td>${esc(r.rollNo)}</td>
          <td>${esc(r.name)}</td>
          <td>${r.total.toFixed(2)}</td>
          <td>${r.percentage.toFixed(2)}%</td>
          <td>${gradeBadge(r.grade)}</td>
        </tr>`).join('')
    : '<tr><td colspan="6" class="empty">No marks entered yet.</td></tr>';
}

async function loadSummary() {
  const stats = await getJson('/api/summary');
  $('summary-rows').innerHTML = stats.length
    ? stats.map((s) => `
        <tr>
          <td><strong>${esc(s.subject)}</strong></td>
          <td>${s.students}</td>
          <td>${s.students ? s.average.toFixed(2) : '-'}</td>
          <td>${s.students ? s.highest.toFixed(2) : '-'}</td>
          <td>${s.students ? s.lowest.toFixed(2) : '-'}</td>
          <td>${s.students ? s.passPercentage.toFixed(1) + '%' : '-'}</td>
          <td>${esc(s.topper)}</td>
        </tr>`).join('')
    : '<tr><td colspan="7" class="empty">No subjects yet.</td></tr>';
}

// ======================= reports =======================

async function loadReports() {
  const [students, dashboard] = await Promise.all([getJson('/api/students'), getJson('/api/dashboard')]);
  fillSelect($('report-student'), students, (s) => s.rollNo,
    (s) => s.rollNo + ' - ' + s.name, $('report-student').value);
  updateStudentDownload();

  // downloads only make sense once some marks exist
  const noData = dashboard.studentsWithMarks === 0;
  ['download-class', 'download-csv', 'download-student'].forEach((id) =>
    $(id).classList.toggle('disabled', noData));
  if (noData) {
    $('report-preview').textContent = 'No marks entered yet, so there is nothing to report.';
  }
}

function updateStudentDownload() {
  const roll = $('report-student').value;
  $('download-student').href = '/api/report/student?roll=' + encodeURIComponent(roll) + '&download=1';
}
$('report-student').addEventListener('change', updateStudentDownload);

// Shows a text report from Java inside the dark preview box
async function previewReport(url) {
  const res = await fetch(url);
  if (!res.ok) {
    const data = await res.json();
    throw new Error(data.error || 'Could not create the report');
  }
  $('report-preview').textContent = await res.text();
}

$('preview-class').addEventListener('click', () => safely(() => previewReport('/api/report/class')));
$('preview-student').addEventListener('click', () => safely(() => {
  const roll = $('report-student').value;
  if (!roll) throw new Error('Choose a student first.');
  return previewReport('/api/report/student?roll=' + encodeURIComponent(roll));
}));

// ======================= start =======================
showPage('overview');
