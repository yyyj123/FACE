const base = {
    get() {
        return {
				url : process.env.VUE_APP_API_BASE_URL || "http://localhost:8080/face/",
				name: process.env.VUE_APP_API_CONTEXT || "face",
			// 退出到首页链接
			indexUrl: process.env.VUE_APP_FRONTEND_URL || 'http://localhost:8082/'
        };
    },
    getProjectName(){
        return {
			projectName: "FACE 美容院店铺管理系统"
        } 
    }
}
export default base
