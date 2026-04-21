import React, { useEffect } from "react";
import { Route, Routes, useLocation } from "react-router-dom";
import Auth from "./pages/Auth";
import View from "./pages/View.jsx";
import { Toaster } from "sonner";
import { useDispatch, useSelector } from "react-redux";
import { isTokenValid } from "./Redux/Auth/isTokenValid.js";
import NavBar from "./components/Navbar/Navbar.jsx";
import Edit from "./pages/Edit.jsx";
import { LOGOUT } from "./Redux/Auth/ActionType.js";

const App = () => {
  const { isAuthenticated, accessToken } = useSelector((store) => store.authStore);
  const dispatch = useDispatch();
  const location = useLocation();

  // Silently log out if the stored token has expired.
  useEffect(() => {
    if (accessToken && !isTokenValid(accessToken)) {
      dispatch({ type: LOGOUT });
    }
  }, [accessToken, dispatch]);

  // The Edit page renders its own NavBar (so it can pass activeUsers).
  // All other authenticated pages share this top-level NavBar.
  const isEditPage = location.pathname.startsWith("/edit/");

  return (
    <>
      <Toaster richColors position="top-right" />
      {!isAuthenticated ? (
        <Auth />
      ) : (
        <div>
          {!isEditPage && <NavBar />}
          <Routes>
            <Route path="/" element={<View />} />
            <Route path="/edit/:docId" element={<Edit />} />
          </Routes>
        </div>
      )}
    </>
  );
};

export default App;
