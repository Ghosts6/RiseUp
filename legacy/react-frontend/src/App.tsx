import { Navigate, Route, Routes } from "react-router-dom";
import Layout from "./components/Layout";
import { useLiveEvents } from "./hooks/useLiveEvents";
import AlarmForm from "./pages/AlarmForm";
import AlarmRinging from "./pages/AlarmRinging";
import Dashboard from "./pages/Dashboard";
import History from "./pages/History";
import Insights from "./pages/Insights";
import Recommendations from "./pages/Recommendations";
import Settings from "./pages/Settings";
import Tasks from "./pages/Tasks";

export default function App() {
  useLiveEvents();
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="alarms/new" element={<AlarmForm />} />
        <Route path="alarms/:id/edit" element={<AlarmForm />} />
        <Route path="alarms/:id/ringing" element={<AlarmRinging />} />
        <Route path="tasks" element={<Tasks />} />
        <Route path="insights" element={<Insights />} />
        <Route path="recommendations" element={<Recommendations />} />
        <Route path="history" element={<History />} />
        <Route path="settings" element={<Settings />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
